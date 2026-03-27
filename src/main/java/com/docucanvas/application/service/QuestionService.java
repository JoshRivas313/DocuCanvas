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

import java.util.List;

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

    public QuestionService(ChatClient.Builder chatClientBuilder,
                           VectorStore vectorStore,
                           ImageGenerationService imageGenerationService,
                           ImageModel imageModel) {
        this.chatClientBuilder = chatClientBuilder;
        this.vectorStore = vectorStore;
        this.imageGenerationService = imageGenerationService;
        this.imageModel = imageModel;
    }

    public QuestionResponse answer(QuestionRequest request) {
        log.info("Procesando pregunta con Spring AI RAG Pipeline: {}", request.question());

        // ── Construir SearchRequest (con filtro opcional por documentId) ──────────
        SearchRequest.Builder searchBuilder = SearchRequest.builder()
                .topK(request.maxChunks())
                .similarityThreshold(0.7);

        if (request.documentId() != null) {
            log.info("Aplicando filtro RAG por documentId: {}", request.documentId());
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            searchBuilder.filterExpression(b.eq("documentId", request.documentId().toString()).build());
        }

        // ── Construir ChatClient con el advisor RAG dinámico ─────────────────────
        ChatClient chatClient = chatClientBuilder
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore, searchBuilder.build()))
                .defaultSystem(SYSTEM_PROMPT)
                .build();

        // ── Invocar el pipeline: recuperación + generación fundada ───────────────
        String answer = chatClient.prompt()
                .user(request.question())
                .call()
                .content();

        log.info("Respuesta generada. Procediendo a generación visual con ImageModel.");

        // ── Generación visual: RAG answer → ImagePrompt → DALL-E ─────────────────
        String imageUrl = "";
        try {
            org.springframework.ai.image.ImagePrompt visualPrompt = imageGenerationService.generateImagePrompt(answer);
            ImageResponse response = imageModel.call(visualPrompt);
            imageUrl = response.getResult().getOutput().getUrl();
            log.info("Imagen generada exitosamente: {}", imageUrl);
        } catch (Exception e) {
            log.error("Fallo en ImageModel (la respuesta textual sigue siendo válida): ", e);
            imageUrl = "Imagen no disponible: " + e.getMessage();
        }

        // ── Fuente real: indicar si se filtró por documento específico ────────────
        String sourceDescription = request.documentId() != null
                ? "Documento: " + request.documentId()
                : "Todos los documentos indexados en VectorStore";

        return new QuestionResponse(
                request.question(),
                answer,
                List.of(sourceDescription),
                request.maxChunks(),
                imageUrl
        );
    }
}

