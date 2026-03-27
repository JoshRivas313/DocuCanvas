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
        log.info("DEBUG DIAGNÓSTICO RAG - Pregunta: {}", request.question());
        
        // Verificación de integridad de la DB
        Integer totalInDb = jdbcClient.sql("SELECT count(*) FROM document_chunks").query(Integer.class).single();
        log.info("DEBUG DIAGNÓSTICO RAG - Total chunks en DB: {}", totalInDb);

        // 1. Recuperación manual de fragmentos para citaciones
        SearchRequest.Builder searchBuilder = SearchRequest.builder()
                .query(request.question())
                .topK(request.maxChunks() != null ? request.maxChunks() : 5)
                .similarityThreshold(0.0);

        if (request.documentId() != null) {
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            searchBuilder.filterExpression(b.eq("documentId", request.documentId().toString()).build());
        }

        List<org.springframework.ai.document.Document> docs = vectorStore.similaritySearch(searchBuilder.build());
        log.info("DEBUG DIAGNÓSTICO RAG - Chunks recuperados por similitud: {}", docs.size());
        
        List<CitationDTO> citations = docs.stream()
                .map(d -> {
                    log.info("DEBUG DIAGNÓSTICO RAG - Fragmento recuperado de: {}", d.getMetadata().get("source"));
                    return new CitationDTO(
                        (String) d.getMetadata().getOrDefault("source", "Documento"),
                        d.getContent(),
                        0.99
                    );
                })
                .toList();

        // 2. Generación fundamentada con ChatClient
        String context = docs.stream()
                .map(org.springframework.ai.document.Document::getContent)
                .collect(java.util.stream.Collectors.joining("\n\n"));

        ChatClient chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();

        String answer = chatClient.prompt()
                .user(u -> u.text("Contexto:\n{context}\n\nPregunta: {question}")
                        .param("context", context.isEmpty() ? "No se encontró contexto relevante." : context)
                        .param("question", request.question()))
                .call()
                .content();

        // Inyectar diagnóstico si falló la búsqueda
        if (docs.isEmpty()) {
            answer = "DIAGNÓSTICO: No recuperé fragmentos. Total en DB: " + totalInDb + ". Revisa si el mapa 3D tiene puntos. \n\n" + answer;
        }

        // 3. Generación visual
        String imageUrl = "";
        try {
            org.springframework.ai.image.ImagePrompt visualPrompt = imageGenerationService.generateImagePrompt(answer);
            ImageResponse imgRes = imageModel.call(visualPrompt);
            imageUrl = imgRes.getResult().getOutput().getUrl();
        } catch (Exception e) {
            log.error("Error en ImageModel: ", e);
            imageUrl = "";
        }

        return new QuestionResponse(
                request.question(),
                answer,
                citations,
                docs.size(),
                imageUrl
        );
    }
}

