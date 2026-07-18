package com.docucanvas.application.service;

import com.docucanvas.application.port.out.ChunkReadPort;
import com.docucanvas.application.question.AnswerQuestionCommand;
import com.docucanvas.application.question.AnswerResult;
import com.docucanvas.application.question.Citation;
import com.docucanvas.application.question.ConceptRelation;
import com.docucanvas.application.question.ConfidenceLevel;
import com.docucanvas.application.question.RetrievalInsights;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Servicio que orquesta el pipeline completo RAG → Generación de texto → Infografía SVG → Informe.
 *
 * <p><b>Timeout dinámico (no una constante fija):</b> una auditoría de rendimiento
 * midió, contra este mismo hardware (CPU, sin GPU), un throughput de prefill de
 * ~30 tokens/seg y de generación de ~5–10 tokens/seg para llama3.2. Un prompt
 * RAG típico de ~1.100 tokens ya consume ~37s solo en prefill. Un timeout fijo
 * de 60s no dejaba margen: el resultado (éxito o "tardó demasiado") dependía de
 * cuántos tokens decidiera generar el modelo esa corrida — no determinista.
 * El timeout ahora se calcula sobre el tamaño real del prompt construido.
 *
 * <p><b>Techo de tokens de salida:</b> antes no había ningún límite (num_predict
 * sin configurar), agravando la falta de determinismo anterior. Ahora se fija
 * explícitamente por request.
 *
 * <p><b>Relevancia objetiva, no juicio subjetivo del modelo:</b> antes se le pedía
 * al LLM decidir si el contexto era "totalmente irrelevante". Un modelo de 3B es
 * inconsistente en ese juicio, sobre todo en la zona de similitud 0.5–0.6 donde
 * caen la mayoría de las recuperaciones reales de este sistema. Ahora la decisión
 * de si hay evidencia disponible se basa en datos objetivos ya calculados
 * (cuántos chunks hay indexados en el alcance de la búsqueda), y el prompt le
 * pide al modelo señalar explícitamente cuando la evidencia es solo parcial —
 * nunca inventar certeza que los datos no respaldan.
 */
@Service
public class QuestionService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(QuestionService.class);

    private static final String SYSTEM_PROMPT = """
            Eres un asistente de DocuCanvas especializado en analizar documentos indexados.

            INSTRUCCIONES:
            1. Usa EXCLUSIVAMENTE el CONTEXTO proporcionado para responder. No inventes datos.
            2. Si el contexto contiene datos concretos (números, fechas, nombres), cítalos textualmente.
            3. Si el contexto es suficiente para sintetizar o resumir, hazlo de forma clara y organizada.
            4. Si el contexto es solo parcialmente relevante para la pregunta (temas relacionados
               pero que no la responden de forma directa), dilo explícitamente con la frase
               "La documentación contiene información relacionada, aunque no responde de forma
               explícita la pregunta." y luego resume lo que sí dice el contexto sobre el tema.
            5. Responde en el mismo idioma de la pregunta.
            6. Al final de tu respuesta, en una línea nueva, agrega SIEMPRE un bloque con este
               formato exacto, usando los "Conceptos detectados" que se te dan (no inventes otros):
               [RELACIONES]
               Concepto1: subtema a, subtema b, subtema c
               Concepto2: subtema d, subtema e
            """;

    // ── Timeout dinámico: constantes calibradas con medición directa (ver javadoc) ──
    private static final double CHARS_PER_TOKEN_ES = 3.7; // medido: 4.186 chars / 1.130 tokens reales
    private static final double PREFILL_TOKENS_PER_SEC = 25.0;   // medido ~30 tok/s; margen de seguridad
    private static final double GENERATION_TOKENS_PER_SEC = 5.0; // medido 5.7-10.5 tok/s; peor caso medido
    private static final long TIMEOUT_MARGIN_SECONDS = 15;
    private static final long MIN_TIMEOUT_SECONDS = 30;
    private static final long MAX_TIMEOUT_SECONDS = 150;
    private static final int MAX_OUTPUT_TOKENS = 400; // num_predict: tope de longitud de la respuesta

    private static final int DEFAULT_MAX_CHUNKS = 5;
    private static final Pattern RELATIONS_BLOCK = Pattern.compile(
            "\\[RELACIONES]\\s*(.*)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATION_LINE = Pattern.compile("^(.+?):\\s*(.+)$");

    private final ChatClient              chatClient;
    private final VectorStore             vectorStore;
    private final ImageGenerationService  imageGenerationService;
    private final ConceptExtractor        conceptExtractor;
    private final ChunkReadPort           chunkReadPort;
    private final String                  ollamaModel;
    private final double                  ollamaTemperature;
    // Ejecutor sobre virtual threads: no fija un techo artificial de concurrencia
    // y se cierra ordenadamente en @PreDestroy.
    private final ExecutorService aiExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public QuestionService(ChatClient.Builder chatClientBuilder,
                           VectorStore vectorStore,
                           ImageGenerationService imageGenerationService,
                           ConceptExtractor conceptExtractor,
                           ChunkReadPort chunkReadPort,
                           @Value("${spring.ai.ollama.chat.options.model}") String ollamaModel,
                           @Value("${spring.ai.ollama.chat.options.temperature}") double ollamaTemperature) {
        this.chatClient             = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.vectorStore            = vectorStore;
        this.imageGenerationService = imageGenerationService;
        this.conceptExtractor       = conceptExtractor;
        this.chunkReadPort          = chunkReadPort;
        this.ollamaModel            = ollamaModel;
        this.ollamaTemperature      = ollamaTemperature;
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
            long now = System.currentTimeMillis();
            return new AnswerResult(
                    request.question(),
                    "No hay documentos indexados" + (documentFilter != null ? " para este filtro" : " en el sistema")
                            + " con los que responder esta pregunta.",
                    List.of(), 0, "", (now - start), 0, 0,
                    new RetrievalInsights(ConfidenceLevel.BAJA, 0.0, List.of(), List.of(), List.of(),
                            0, "No se ejecutó búsqueda semántica: no hay chunks indexados en el alcance solicitado.", false));
        }

        // ── 1. Recuperación (Retrieval) ───────────────────────────────────
        int topK = request.maxChunks() != null ? request.maxChunks() : DEFAULT_MAX_CHUNKS;
        List<org.springframework.ai.document.Document> docs = retrieve(request.question(), topK, documentFilter);
        long retrievalEnd = System.currentTimeMillis();

        List<Citation> citations = docs.stream()
                .map(d -> new Citation(
                        (String) d.getMetadata().getOrDefault("source", "Documento"),
                        d.getText(),
                        similarityScore(d),
                        pageOf(d)))
                .toList();

        // ── 2. Preparar contexto + conceptos compartido ───────────────────
        String context = docs.stream()
                .map(org.springframework.ai.document.Document::getText)
                .collect(Collectors.joining("\n\n"));
        List<String> keyConcepts = conceptExtractor.extractKeyConcepts(context, 6);

        String userPromptText = buildUserPrompt(context, keyConcepts, request.question());
        long promptTokensEstimate = estimateTokens(SYSTEM_PROMPT, userPromptText);
        long dynamicTimeoutSeconds = computeDynamicTimeoutSeconds(promptTokensEstimate);
        log.info("Prompt RAG: ~{} tokens estimados ({} chunks) -> timeout dinámico {}s",
                promptTokensEstimate, docs.size(), dynamicTimeoutSeconds);

        // ── 3. Lanzar LLM + SVG EN PARALELO ──────────────────────────────
        OllamaOptions requestOptions = OllamaOptions.builder()
                .model(ollamaModel)
                .temperature(ollamaTemperature)
                .numPredict(MAX_OUTPUT_TOKENS)
                .build();

        CompletableFuture<String> rawResponseFuture = CompletableFuture.supplyAsync(() ->
            chatClient.prompt()
                    .options(requestOptions)
                    .user(userPromptText)
                    .call()
                    .content(),
            aiExecutor);

        CompletableFuture<String> imageFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return imageGenerationService.generateImageDataUrl(request.question(), context);
            } catch (Exception e) {
                log.error("Error generando infografía SVG: ", e);
                return "";
            }
        }, aiExecutor);

        // ── 4. Esperar ambas tareas (con timeout dinámico) ────────────────
        String rawResponse;
        String imageUrl;
        try {
            rawResponse = rawResponseFuture.get(dynamicTimeoutSeconds, TimeUnit.SECONDS);
            imageUrl    = imageFuture.get(dynamicTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Timeout ({}s, prompt ~{} tokens) esperando al modelo de IA",
                    dynamicTimeoutSeconds, promptTokensEstimate, e);
            rawResponseFuture.cancel(true);
            imageFuture.cancel(true);
            rawResponse = "El modelo de IA tardó demasiado en responder. Inténtalo de nuevo.";
            imageUrl    = "";
        } catch (Exception e) {
            log.error("Error esperando tareas paralelas: ", e);
            rawResponse = "Error al procesar la pregunta.";
            imageUrl    = "";
        }

        long generationEnd = System.currentTimeMillis();

        // ── 5. Separar respuesta visible del bloque [RELACIONES] ──────────
        String answer = extractAnswer(rawResponse);
        List<ConceptRelation> relations = parseRelations(rawResponse, keyConcepts);

        // ── 6. Insights de transparencia ───────────────────────────────────
        double avgScore = citations.stream()
                .map(Citation::score).filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
        ConfidenceLevel confidenceLevel = ConfidenceLevel.fromAverageScore(avgScore);
        List<String> documentsUsed = citations.stream().map(Citation::source).distinct().toList();
        boolean partialMatch = confidenceLevel != ConfidenceLevel.ALTA;
        String howItWasFound = buildHowItWasFound(totalChunksAnalyzed, documentFilter, citations.size());

        RetrievalInsights insights = new RetrievalInsights(
                confidenceLevel, avgScore, keyConcepts, relations, documentsUsed,
                totalChunksAnalyzed, howItWasFound, partialMatch);

        return new AnswerResult(
                request.question(),
                answer,
                citations,
                docs.size(),
                imageUrl,
                (retrievalEnd - start),
                (generationEnd - retrievalEnd),
                0, // La imagen se generó en paralelo, no suma al tiempo secuencial
                insights);
    }

    private List<org.springframework.ai.document.Document> retrieve(String question, int topK, String documentFilter) {
        SearchRequest.Builder searchBuilder = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(0.3);
        applyFilter(searchBuilder, documentFilter);

        List<org.springframework.ai.document.Document> docs = vectorStore.similaritySearch(searchBuilder.build());

        // Fallback: si el umbral 0.3 no devuelve nada, reintentar sin umbral
        // para preguntas abstractas o de síntesis que tienen baja similitud coseno
        if (docs.isEmpty()) {
            log.info("Retrieval vacío con umbral 0.3 — reintentando sin umbral para: {}", question);
            SearchRequest.Builder fallbackBuilder = SearchRequest.builder()
                    .query(question)
                    .topK(topK)
                    .similarityThreshold(0.0);
            applyFilter(fallbackBuilder, documentFilter);
            docs = vectorStore.similaritySearch(fallbackBuilder.build());
            log.info("Fallback retrieval devolvió {} chunks", docs.size());
        }
        return docs;
    }

    private void applyFilter(SearchRequest.Builder builder, String documentFilter) {
        if (documentFilter != null) {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            builder.filterExpression(b.eq("documentId", documentFilter).build());
        }
    }

    private String buildUserPrompt(String context, List<String> keyConcepts, String question) {
        String contextForPrompt = context.isEmpty() ? "No se encontró contexto relevante en los documentos." : context;
        String conceptsLine = keyConcepts.isEmpty() ? "(ninguno detectado)" : String.join(", ", keyConcepts);
        return "Contexto:\n" + contextForPrompt
                + "\n\nConceptos detectados: " + conceptsLine
                + "\n\nPregunta: " + question;
    }

    /** Estimación de tokens vía caracteres/token calibrado con medición real (ver constante CHARS_PER_TOKEN_ES). */
    private long estimateTokens(String systemPrompt, String userPrompt) {
        return Math.round((systemPrompt.length() + userPrompt.length()) / CHARS_PER_TOKEN_ES);
    }

    private long computeDynamicTimeoutSeconds(long promptTokensEstimate) {
        double prefillSeconds = promptTokensEstimate / PREFILL_TOKENS_PER_SEC;
        double generationSeconds = MAX_OUTPUT_TOKENS / GENERATION_TOKENS_PER_SEC;
        long total = Math.round(prefillSeconds + generationSeconds) + TIMEOUT_MARGIN_SECONDS;
        return Math.min(MAX_TIMEOUT_SECONDS, Math.max(MIN_TIMEOUT_SECONDS, total));
    }

    /** Corta la respuesta del modelo justo antes del bloque [RELACIONES], si existe. */
    private String extractAnswer(String rawResponse) {
        if (rawResponse == null) return "";
        int idx = indexOfRelationsBlock(rawResponse);
        String answer = idx >= 0 ? rawResponse.substring(0, idx) : rawResponse;
        return answer.trim();
    }

    private int indexOfRelationsBlock(String text) {
        Matcher m = Pattern.compile("\\[RELACIONES]", Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? m.start() : -1;
    }

    /**
     * Parsea el bloque {@code [RELACIONES]} del final de la respuesta. Si el
     * modelo no siguió el formato, devuelve una lista vacía en vez de fallar
     * la respuesta principal — el bloque es un adjunto informativo, no crítico.
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

    private String buildHowItWasFound(int totalChunksAnalyzed, String documentFilter, int chunksRetrieved) {
        String scope = documentFilter != null ? "del documento seleccionado" : "en todo el sistema";
        return String.format(
                "Esta respuesta fue obtenida mediante búsqueda semántica sobre la base vectorial. "
                + "Se analizaron %d fragmentos indexados %s. Los embeddings de la consulta fueron "
                + "comparados usando similitud del coseno. Se recuperaron los %d fragmentos más "
                + "relevantes. Posteriormente, el modelo de lenguaje generó esta respuesta utilizando "
                + "únicamente esa evidencia.",
                totalChunksAnalyzed, scope, chunksRetrieved);
    }

    private Integer pageOf(org.springframework.ai.document.Document d) {
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
    private Double similarityScore(org.springframework.ai.document.Document d) {
        Object distance = d.getMetadata().get("distance");
        if (distance instanceof Number n) {
            double similarity = 1.0 - n.doubleValue();
            return Math.max(0.0, Math.min(1.0, similarity));
        }
        return null;
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
