package com.docucanvas.application.service;

import com.docucanvas.application.port.out.ChunkReadPort;
import com.docucanvas.application.question.AnswerQuestionCommand;
import com.docucanvas.application.question.AnswerResult;
import com.docucanvas.application.question.Citation;
import com.docucanvas.application.question.ConceptRelation;
import com.docucanvas.application.question.ConfidenceLevel;
import com.docucanvas.application.question.RetrievalInsights;
import com.docucanvas.application.rag.RagPromptFactory;
import com.docucanvas.application.rag.RagRetriever;
import com.docucanvas.application.rag.RetrievalResult;
import com.docucanvas.infrastructure.config.RagProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orquesta el pipeline RAG: recuperación → construcción de contexto → generación
 * → ensamblado de la respuesta con sus metadatos de transparencia.
 *
 * <p><b>Qué dejó de hacer este servicio:</b> la versión base concentraba aquí la
 * búsqueda vectorial, la construcción del prompt, la gestión de hilos y
 * timeouts, el parseo de la salida y el cálculo de insights — 385 líneas y al
 * menos cuatro responsabilidades que cambian por razones distintas. La
 * recuperación vive ahora en {@link RagRetriever} y los prompts en
 * {@link RagPromptFactory}; aquí queda la orquestación, que es lo que el nombre
 * de la clase promete.
 *
 * <p><b>Opciones de modelo portables:</b> se usa {@link ChatOptions} en lugar de
 * {@code OllamaOptions}. Con las opciones específicas del proveedor, cambiar de
 * Ollama a Gemini obligaba a tocar este archivo; con la interfaz portable, el
 * cambio de proveedor es una dependencia y una propiedad, sin recompilar lógica
 * de negocio. Es la promesa concreta de la abstracción de Spring AI.
 *
 * <p><b>Timeout dinámico:</b> una auditoría de rendimiento midió sobre este mismo
 * hardware (CPU, sin GPU) un throughput de prefill de ~30 tokens/seg y de
 * generación de ~5–10 tokens/seg para llama3.2. Un prompt RAG típico de ~1.100
 * tokens consume ~37s solo en prefill, así que un timeout fijo de 60s hacía que
 * el resultado dependiera de cuántos tokens decidiera generar el modelo esa
 * corrida. El timeout se calcula sobre el tamaño real del prompt construido, con
 * las constantes de calibración externalizadas en
 * {@link RagProperties.Generation}.
 */
@Service
public class QuestionService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(QuestionService.class);

    private static final Pattern RELATIONS_BLOCK = Pattern.compile(
            "\\[RELACIONES]\\s*(.*)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATION_LINE = Pattern.compile("^(.+?):\\s*(.+)$");
    private static final Pattern RELATIONS_MARKER = Pattern.compile(
            "\\[RELACIONES]", Pattern.CASE_INSENSITIVE);

    private final ChatClient chatClient;
    private final RagRetriever ragRetriever;
    private final RagPromptFactory promptFactory;
    private final ImageGenerationService imageGenerationService;
    private final ConceptExtractor conceptExtractor;
    private final ChunkReadPort chunkReadPort;
    private final RagProperties.Generation generationConfig;
    private final String chatModel;
    private final double temperature;

    // Ejecutor sobre virtual threads: no fija un techo artificial de concurrencia
    // y se cierra ordenadamente en @PreDestroy.
    private final ExecutorService aiExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public QuestionService(ChatClient.Builder chatClientBuilder,
                           RagRetriever ragRetriever,
                           RagPromptFactory promptFactory,
                           ImageGenerationService imageGenerationService,
                           ConceptExtractor conceptExtractor,
                           ChunkReadPort chunkReadPort,
                           RagProperties ragProperties,
                           @Value("${spring.ai.ollama.chat.options.model}") String chatModel,
                           @Value("${spring.ai.ollama.chat.options.temperature}") double temperature) {
        this.promptFactory = promptFactory;
        this.chatClient = chatClientBuilder.defaultSystem(promptFactory.systemPrompt()).build();
        this.ragRetriever = ragRetriever;
        this.imageGenerationService = imageGenerationService;
        this.conceptExtractor = conceptExtractor;
        this.chunkReadPort = chunkReadPort;
        this.generationConfig = ragProperties.generation();
        this.chatModel = chatModel;
        this.temperature = temperature;
    }

    public AnswerResult answer(AnswerQuestionCommand request) {
        log.info("Iniciando Pipeline RAG para: {}", request.question());
        long start = System.currentTimeMillis();

        // ── 0. Alcance de la búsqueda: ¿hay algo indexado? ────────────────
        String documentFilter = request.documentId() != null ? request.documentId().toString() : null;
        int totalChunksAnalyzed = documentFilter != null
                ? chunkReadPort.countByDocument(documentFilter)
                : chunkReadPort.countAll();

        if (totalChunksAnalyzed == 0) {
            return noIndexedContent(request, documentFilter, start);
        }

        // ── 1. Recuperación ───────────────────────────────────────────────
        RetrievalResult retrieval = ragRetriever.retrieve(
                request.question(), request.maxChunks(), documentFilter);
        long retrievalEnd = System.currentTimeMillis();

        List<Citation> citations = retrieval.documents().stream()
                .map(this::toCitation)
                .toList();

        // ── 2. Contexto y conceptos ───────────────────────────────────────
        String context = promptFactory.buildContext(retrieval.documents());
        List<String> keyConcepts = conceptExtractor.extractKeyConcepts(context, 6);
        String userPrompt = promptFactory.buildUserPrompt(context, keyConcepts, request.question());

        long promptTokens = estimateTokens(userPrompt);
        long timeoutSeconds = computeDynamicTimeoutSeconds(promptTokens);
        log.info("Prompt RAG: ~{} tokens estimados ({} chunks) -> timeout dinámico {}s",
                promptTokens, retrieval.documents().size(), timeoutSeconds);

        // ── 3. Generación de texto e infografía ───────────────────────────
        CompletableFuture<String> textFuture = CompletableFuture.supplyAsync(
                () -> generateText(userPrompt), aiExecutor);
        CompletableFuture<String> imageFuture = CompletableFuture.supplyAsync(
                () -> generateImageSafely(request.question(), context), aiExecutor);

        String rawResponse;
        String imageUrl;
        try {
            rawResponse = textFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            imageUrl = imageFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Timeout ({}s, prompt ~{} tokens) esperando al modelo de IA",
                    timeoutSeconds, promptTokens, e);
            textFuture.cancel(true);
            imageFuture.cancel(true);
            rawResponse = "El modelo de IA tardó demasiado en responder. Inténtalo de nuevo.";
            imageUrl = "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrumpido esperando al modelo de IA", e);
            rawResponse = "La generación fue interrumpida.";
            imageUrl = "";
        } catch (Exception e) {
            log.error("Error esperando tareas paralelas: ", e);
            rawResponse = "Error al procesar la pregunta.";
            imageUrl = "";
        }

        long generationEnd = System.currentTimeMillis();

        // ── 4. Ensamblado ─────────────────────────────────────────────────
        String answer = extractAnswer(rawResponse);
        List<ConceptRelation> relations = parseRelations(rawResponse, keyConcepts);

        RetrievalInsights insights = buildInsights(
                citations, keyConcepts, relations, totalChunksAnalyzed, documentFilter, retrieval);

        return new AnswerResult(
                request.question(),
                answer,
                citations,
                retrieval.documents().size(),
                imageUrl,
                (retrievalEnd - start),
                (generationEnd - retrievalEnd),
                0, // la imagen se generó en paralelo, no suma al tiempo secuencial
                insights);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Generación
    // ─────────────────────────────────────────────────────────────────────

    private String generateText(String userPrompt) {
        return chatClient.prompt()
                .options(chatOptions())
                .user(userPrompt)
                .call()
                .content();
    }

    /**
     * Opciones portables: {@code maxTokens} es el equivalente neutral de
     * {@code num_predict} de Ollama y de {@code maxOutputTokens} de Gemini. El
     * proveedor concreto las traduce a su propio dialecto.
     */
    private ChatOptions chatOptions() {
        return ChatOptions.builder()
                .model(chatModel)
                .temperature(temperature)
                .maxTokens(generationConfig.maxOutputTokens())
                .build();
    }

    private String generateImageSafely(String question, String context) {
        try {
            return imageGenerationService.generateImageDataUrl(question, context);
        } catch (Exception e) {
            log.error("Error generando infografía SVG: ", e);
            return "";
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Ensamblado de la respuesta
    // ─────────────────────────────────────────────────────────────────────

    private AnswerResult noIndexedContent(AnswerQuestionCommand request, String documentFilter, long start) {
        long now = System.currentTimeMillis();
        return new AnswerResult(
                request.question(),
                "No hay documentos indexados" + (documentFilter != null ? " para este filtro" : " en el sistema")
                        + " con los que responder esta pregunta.",
                List.of(), 0, "", (now - start), 0, 0,
                new RetrievalInsights(ConfidenceLevel.BAJA, 0.0, List.of(), List.of(), List.of(),
                        0, "No se ejecutó búsqueda semántica: no hay chunks indexados en el alcance solicitado.",
                        false));
    }

    private RetrievalInsights buildInsights(List<Citation> citations,
                                            List<String> keyConcepts,
                                            List<ConceptRelation> relations,
                                            int totalChunksAnalyzed,
                                            String documentFilter,
                                            RetrievalResult retrieval) {
        double avgScore = citations.stream()
                .map(Citation::score).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
        ConfidenceLevel confidenceLevel = ConfidenceLevel.fromAverageScore(avgScore);
        List<String> documentsUsed = citations.stream().map(Citation::source).distinct().toList();

        return new RetrievalInsights(
                confidenceLevel,
                avgScore,
                keyConcepts,
                relations,
                documentsUsed,
                totalChunksAnalyzed,
                buildHowItWasFound(totalChunksAnalyzed, documentFilter, citations.size(), retrieval),
                confidenceLevel != ConfidenceLevel.ALTA);
    }

    private String buildHowItWasFound(int totalChunksAnalyzed, String documentFilter,
                                      int chunksRetrieved, RetrievalResult retrieval) {
        String scope = documentFilter != null ? "del documento seleccionado" : "en todo el sistema";
        String base = String.format(
                "Esta respuesta fue obtenida mediante búsqueda semántica sobre la base vectorial. "
                + "Se analizaron %d fragmentos indexados %s. Los embeddings de la consulta fueron "
                + "comparados usando similitud del coseno. Se recuperaron los %d fragmentos más "
                + "relevantes. Posteriormente, el modelo de lenguaje generó esta respuesta utilizando "
                + "únicamente esa evidencia.",
                totalChunksAnalyzed, scope, chunksRetrieved);

        // Transparencia real: si hubo que relajar el umbral, el usuario merece saberlo.
        if (retrieval.usedRelaxedThreshold()) {
            base += " Ningún fragmento superó el umbral de similitud habitual, así que la búsqueda "
                    + "se repitió sin umbral: la evidencia recuperada es la más cercana disponible, "
                    + "no necesariamente una coincidencia fuerte.";
        }
        return base;
    }

    private Citation toCitation(Document d) {
        return new Citation(
                (String) d.getMetadata().getOrDefault("source", "Documento"),
                d.getText(),
                similarityScore(d),
                pageOf(d));
    }

    private Integer pageOf(Document d) {
        Object page = d.getMetadata().get("page");
        if (page instanceof Number n) return n.intValue();
        if (page instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { /* sin página válida */ }
        }
        return null;
    }

    /**
     * Deriva la similitud coseno real (0..1) de un documento recuperado.
     *
     * <p>PGVector expone la distancia coseno en la metadata bajo la clave
     * {@code distance}; la similitud es {@code 1 - distancia}. Si el store no la
     * proporciona, se devuelve {@code null} (score desconocido) en lugar de un
     * valor inventado.
     */
    private Double similarityScore(Document d) {
        Object distance = d.getMetadata().get("distance");
        if (distance instanceof Number n) {
            double similarity = 1.0 - n.doubleValue();
            return Math.max(0.0, Math.min(1.0, similarity));
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Timeout dinámico
    // ─────────────────────────────────────────────────────────────────────

    private long estimateTokens(String userPrompt) {
        int chars = promptFactory.systemPrompt().length() + userPrompt.length();
        return Math.round(chars / generationConfig.charsPerToken());
    }

    private long computeDynamicTimeoutSeconds(long promptTokens) {
        double prefillSeconds = promptTokens / generationConfig.prefillTokensPerSecond();
        double generationSeconds = generationConfig.maxOutputTokens() / generationConfig.outputTokensPerSecond();
        long total = Math.round(prefillSeconds + generationSeconds) + generationConfig.timeoutMarginSeconds();
        return Math.min(generationConfig.maxTimeoutSeconds(),
                Math.max(generationConfig.minTimeoutSeconds(), total));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Parseo del bloque [RELACIONES]
    // ─────────────────────────────────────────────────────────────────────

    /** Corta la respuesta del modelo justo antes del bloque [RELACIONES], si existe. */
    private String extractAnswer(String rawResponse) {
        if (rawResponse == null) return "";
        Matcher marker = RELATIONS_MARKER.matcher(rawResponse);
        String answer = marker.find() ? rawResponse.substring(0, marker.start()) : rawResponse;
        return answer.trim();
    }

    /**
     * Parsea el bloque {@code [RELACIONES]} del final de la respuesta. Si el
     * modelo no siguió el formato, devuelve una lista vacía en vez de fallar la
     * respuesta principal — el bloque es un adjunto informativo, no crítico.
     */
    private List<ConceptRelation> parseRelations(String rawResponse, List<String> keyConcepts) {
        if (rawResponse == null || keyConcepts.isEmpty()) return List.of();
        Matcher blockMatcher = RELATIONS_BLOCK.matcher(rawResponse);
        if (!blockMatcher.find()) return List.of();

        Map<String, ConceptRelation> byConcept = new LinkedHashMap<>();
        for (String line : blockMatcher.group(1).split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            Matcher lineMatcher = RELATION_LINE.matcher(trimmed);
            if (!lineMatcher.matches()) continue;
            String concept = lineMatcher.group(1).trim();
            List<String> related = new ArrayList<>();
            for (String topic : lineMatcher.group(2).split(",")) {
                String t = topic.trim();
                if (!t.isEmpty()) related.add(t);
            }
            if (!related.isEmpty()) {
                byConcept.put(concept, new ConceptRelation(concept, related));
            }
        }
        return List.copyOf(byConcept.values());
    }

    @PreDestroy
    void shutdownExecutor() {
        aiExecutor.shutdown();
        try {
            if (!aiExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                aiExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            aiExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
