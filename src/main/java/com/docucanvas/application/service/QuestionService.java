package com.docucanvas.application.service;

import com.docucanvas.application.question.AnswerQuestionCommand;
import com.docucanvas.application.question.AnswerResult;
import com.docucanvas.application.question.Citation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Servicio que orquesta el pipeline completo RAG → Generación de texto → Infografía SVG.
 *
 * <p><b>Cambio respecto a la versión OpenAI:</b><br>
 * Se eliminó la dependencia de {@code ImageModel} (DALL-E 3) ya que Ollama
 * no soporta generación de imágenes. En su lugar, {@link ImageGenerationService}
 * produce una infografía SVG 100% local a partir del contexto recuperado.
 *
 * <p>El pipeline sigue siendo concurrente: el LLM de Ollama genera la respuesta
 * textual mientras se construye la infografía SVG en paralelo, usando los mismos
 * chunks del VectorStore como fuente de datos.
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
            4. Solo di "No encontré esa información en los documentos indexados." cuando el contexto
               esté completamente vacío o sea totalmente irrelevante para la pregunta.
            5. Responde en el mismo idioma de la pregunta.
            """;

    /** Tiempo máximo de espera por cada tarea paralela (LLM / infografía). */
    private static final long AI_TASK_TIMEOUT_SECONDS = 60;
    private static final int  DEFAULT_MAX_CHUNKS       = 5;

    private final ChatClient             chatClient;
    private final VectorStore            vectorStore;
    private final ImageGenerationService imageGenerationService;
    // Ejecutor sobre virtual threads: no fija un techo artificial de concurrencia
    // y se cierra ordenadamente en @PreDestroy.
    private final ExecutorService        aiExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public QuestionService(ChatClient.Builder chatClientBuilder,
                           VectorStore vectorStore,
                           ImageGenerationService imageGenerationService) {
        // Construido una sola vez: evita reconstruir el cliente en cada pregunta.
        this.chatClient             = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.vectorStore            = vectorStore;
        this.imageGenerationService = imageGenerationService;
    }

    public AnswerResult answer(AnswerQuestionCommand request) {
        log.info("Iniciando Pipeline RAG para: {}", request.question());
        long start = System.currentTimeMillis();

        // ── 1. Recuperación (Retrieval) ───────────────────────────────────
        int topK = request.maxChunks() != null ? request.maxChunks() : DEFAULT_MAX_CHUNKS;

        SearchRequest.Builder searchBuilder = SearchRequest.builder()
                .query(request.question())
                .topK(topK)
                .similarityThreshold(0.3);

        if (request.documentId() != null) {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            searchBuilder.filterExpression(
                    b.eq("documentId", request.documentId().toString()).build());
        }

        List<org.springframework.ai.document.Document> docs =
                vectorStore.similaritySearch(searchBuilder.build());

        // Fallback: si el umbral 0.3 no devuelve nada, reintentar sin umbral
        // para preguntas abstractas o de síntesis que tienen baja similitud coseno
        if (docs.isEmpty()) {
            log.info("Retrieval vacío con umbral 0.3 — reintentando sin umbral para: {}", request.question());
            SearchRequest.Builder fallbackBuilder = SearchRequest.builder()
                    .query(request.question())
                    .topK(topK)
                    .similarityThreshold(0.0);
            if (request.documentId() != null) {
                FilterExpressionBuilder b = new FilterExpressionBuilder();
                fallbackBuilder.filterExpression(
                        b.eq("documentId", request.documentId().toString()).build());
            }
            docs = vectorStore.similaritySearch(fallbackBuilder.build());
            log.info("Fallback retrieval devolvió {} chunks", docs.size());
        }

        long retrievalEnd = System.currentTimeMillis();

        List<Citation> citations = docs.stream()
                .map(d -> new Citation(
                        (String) d.getMetadata().getOrDefault("source", "Documento"),
                        d.getText(),
                        similarityScore(d)))
                .toList();

        // ── 2. Preparar contexto compartido ──────────────────────────────
        String context = docs.stream()
                .map(org.springframework.ai.document.Document::getText)
                .collect(Collectors.joining("\n\n"));

        // ── 3. Lanzar LLM + SVG EN PARALELO ──────────────────────────────

        // Tarea A: Generación de respuesta textual (Ollama LLM)
        CompletableFuture<String> answerFuture = CompletableFuture.supplyAsync(() ->
            chatClient.prompt()
                    .user(u -> u.text("Contexto:\n{context}\n\nPregunta: {question}")
                            .param("context", context.isEmpty()
                                    ? "No se encontró contexto relevante en los documentos."
                                    : context)
                            .param("question", request.question()))
                    .call()
                    .content(),
            aiExecutor);

        // Tarea B: Generación de infografía SVG local (sin API externa, sin GPU)
        CompletableFuture<String> imageFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return imageGenerationService.generateImageDataUrl(request.question(), context);
            } catch (Exception e) {
                log.error("Error generando infografía SVG: ", e);
                return "";
            }
        }, aiExecutor);

        // ── 4. Esperar ambas tareas (con timeout para no bloquear el hilo HTTP) ──
        String answer;
        String imageUrl;
        try {
            answer   = answerFuture.get(AI_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            imageUrl = imageFuture.get(AI_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Timeout ({}s) esperando al modelo de IA", AI_TASK_TIMEOUT_SECONDS, e);
            answerFuture.cancel(true);
            imageFuture.cancel(true);
            answer   = "El modelo de IA tardó demasiado en responder. Inténtalo de nuevo.";
            imageUrl = "";
        } catch (Exception e) {
            log.error("Error esperando tareas paralelas: ", e);
            answer   = "Error al procesar la pregunta.";
            imageUrl = "";
        }

        long generationEnd = System.currentTimeMillis();

        return new AnswerResult(
                request.question(),
                answer,
                citations,
                docs.size(),
                imageUrl,
                (retrievalEnd - start),
                (generationEnd - retrievalEnd),
                0 // La imagen se generó en paralelo, no suma al tiempo secuencial
        );
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
