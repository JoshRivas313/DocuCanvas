package com.docucanvas.application.service;

import com.docucanvas.api.dto.request.QuestionRequest;
import com.docucanvas.api.dto.response.QuestionResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageResponse;
import org.springframework.stereotype.Service;

import com.docucanvas.api.dto.response.CitationDTO;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Servicio que orquesta el pipeline completo RAG → Generación → Imagen.
 *
 * <p>Utiliza {@link QuestionAnswerAdvisor} de Spring AI para enriquecer
 * automáticamente el prompt con contexto documental recuperado del VectorStore.
 */
@Service
public class QuestionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QuestionService.class);

    private static final String SYSTEM_PROMPT = """
            Eres un asistente experto en análisis de documentos de DocuCanvas.
            Responde de manera profesional usando el contexto recuperado.
            Si no puedes responder con el contexto, admítelo educadamente.
            """;

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final ImageGenerationService imageGenerationService;
    private final ImageModel imageModel;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbcClient;
    private final ExecutorService aiExecutor = Executors.newFixedThreadPool(2);

    public QuestionService(ChatClient.Builder chatClientBuilder,
                           VectorStore vectorStore,
                           ImageGenerationService imageGenerationService,
                           ImageModel imageModel,
                           org.springframework.jdbc.core.simple.JdbcClient jdbcClient) {
        this.chatClientBuilder = chatClientBuilder;
        this.vectorStore = vectorStore;
        this.imageGenerationService = imageGenerationService;
        this.imageModel = imageModel;
        this.jdbcClient = jdbcClient;
    }

    public QuestionResponse answer(QuestionRequest request) {
        log.info("Iniciando Pipeline RAG para: {}", request.question());
        long start = System.currentTimeMillis();

        // 1. Recuperación (Retrieval)
        SearchRequest.Builder searchBuilder = SearchRequest.builder()
                .query(request.question())
                .topK(request.maxChunks() != null ? request.maxChunks() : 5)
                .similarityThreshold(0.7); // Restaurado a nivel óptimo para producción

        if (request.documentId() != null) {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            searchBuilder.filterExpression(b.eq("documentId", request.documentId().toString()).build());
        }

        List<org.springframework.ai.document.Document> docs = vectorStore.similaritySearch(searchBuilder.build());
        long retrievalEnd = System.currentTimeMillis();
        
        List<CitationDTO> citations = docs.stream()
                .map(d -> new CitationDTO(
                        (String) d.getMetadata().getOrDefault("source", "Documento"),
                        d.getContent(),
                        0.99
                ))
                .toList();

        // 2. Lanzamos LLM + ImageModel EN PARALELO
        String context = docs.stream()
                .map(org.springframework.ai.document.Document::getContent)
                .collect(Collectors.joining("\n\n"));

        // Tarea A: Generación de Texto (LLM)
        CompletableFuture<String> answerFuture = CompletableFuture.supplyAsync(() -> {
            ChatClient chatClient = chatClientBuilder
                    .defaultSystem(SYSTEM_PROMPT)
                    .build();

            return chatClient.prompt()
                    .user(u -> u.text("Contexto:\n{context}\n\nPregunta: {question}")
                            .param("context", context.isEmpty() ? "No se encontró contexto relevante." : context)
                            .param("question", request.question()))
                    .call()
                    .content();
        }, aiExecutor);

        // Tarea B: Generación Visual (ImageModel) — usa contexto directo, no espera al LLM
        CompletableFuture<String> imageFuture = CompletableFuture.supplyAsync(() -> {
            try {
                org.springframework.ai.image.ImagePrompt visualPrompt =
                        imageGenerationService.generateImagePromptFromContext(request.question(), context);
                ImageResponse imgRes = imageModel.call(visualPrompt);
                return imgRes.getResult().getOutput().getUrl();
            } catch (Exception e) {
                log.error("Error en ImageModel (paralelo): ", e);
                return "";
            }
        }, aiExecutor);

        // Esperamos ambas tareas concurrentes
        String answer;
        String imageUrl;
        try {
            answer = answerFuture.get();
            imageUrl = imageFuture.get();
        } catch (Exception e) {
            log.error("Error esperando tareas paralelas: ", e);
            answer = "Error al procesar la pregunta.";
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
