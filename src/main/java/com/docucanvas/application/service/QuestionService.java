package com.docucanvas.application.service;

import com.docucanvas.api.dto.request.QuestionRequest;
import com.docucanvas.api.dto.response.QuestionResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import com.docucanvas.api.dto.response.CitationDTO;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private final ChatClient.Builder     chatClientBuilder;
    private final VectorStore            vectorStore;
    private final ImageGenerationService imageGenerationService;
    private final ExecutorService        aiExecutor = Executors.newFixedThreadPool(2);

    public QuestionService(ChatClient.Builder chatClientBuilder,
                           VectorStore vectorStore,
                           ImageGenerationService imageGenerationService) {
        this.chatClientBuilder      = chatClientBuilder;
        this.vectorStore            = vectorStore;
        this.imageGenerationService = imageGenerationService;
    }

    public QuestionResponse answer(QuestionRequest request) {
        log.info("Iniciando Pipeline RAG para: {}", request.question());
        long start = System.currentTimeMillis();

        // ── 1. Recuperación (Retrieval) ───────────────────────────────────
        int topK = request.maxChunks() != null ? request.maxChunks() : 5;

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

        List<CitationDTO> citations = docs.stream()
                .map(d -> new CitationDTO(
                        (String) d.getMetadata().getOrDefault("source", "Documento"),
                        d.getContent(),
                        0.99))
                .toList();

        // ── 2. Preparar contexto compartido ──────────────────────────────
        String context = docs.stream()
                .map(org.springframework.ai.document.Document::getContent)
                .collect(Collectors.joining("\n\n"));

        // ── 3. Lanzar LLM + SVG EN PARALELO ──────────────────────────────

        // Tarea A: Generación de respuesta textual (Ollama LLM)
        CompletableFuture<String> answerFuture = CompletableFuture.supplyAsync(() -> {
            ChatClient chatClient = chatClientBuilder
                    .defaultSystem(SYSTEM_PROMPT)
                    .build();

            return chatClient.prompt()
                    .user(u -> u.text("Contexto:\n{context}\n\nPregunta: {question}")
                            .param("context", context.isEmpty()
                                    ? "No se encontró contexto relevante en los documentos."
                                    : context)
                            .param("question", request.question()))
                    .call()
                    .content();
        }, aiExecutor);

        // Tarea B: Generación de infografía SVG local (sin API externa, sin GPU)
        CompletableFuture<String> imageFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return imageGenerationService.generateImageDataUrl(request.question(), context);
            } catch (Exception e) {
                log.error("Error generando infografía SVG: ", e);
                return "";
            }
        }, aiExecutor);

        // ── 4. Esperar ambas tareas ───────────────────────────────────────
        String answer;
        String imageUrl;
        try {
            answer   = answerFuture.get();
            imageUrl = imageFuture.get();
        } catch (Exception e) {
            log.error("Error esperando tareas paralelas: ", e);
            answer   = "Error al procesar la pregunta.";
            imageUrl = "";
        }

        long generationEnd = System.currentTimeMillis();

        return new QuestionResponse(
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
}
