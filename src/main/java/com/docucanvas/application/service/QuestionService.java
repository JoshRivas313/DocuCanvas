package com.docucanvas.application.service;

import com.docucanvas.api.dto.request.QuestionRequest;
import com.docucanvas.api.dto.response.QuestionResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QuestionService.class);
    
    private static final String SYSTEM_PROMPT = """
            Eres un asistente experto en análisis de documentos de DocuCanvas.
            Responde de manera profesional usando el contexto recuperado.
            Si no puedes responder con el contexto, admítelo educadamente.
            """;

    private final ChatClient chatClient;
    private final ImageGenerationService imageGenerationService;

    public QuestionService(ChatClient.Builder chatClientBuilder,
                           VectorStore vectorStore,
                           ImageGenerationService imageGenerationService) {
        this.imageGenerationService = imageGenerationService;
        
        // Configuramos el ChatClient con el Advisor de RAG nativo
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder()
                        .topK(4)
                        .similarityThreshold(0.7)
                        .build()))
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    public QuestionResponse answer(QuestionRequest request) {
        log.info("Procesando pregunta con Spring AI RAG Pipeline: {}", request.question());

        // El flujo RAG ocurre automáticamente gracias al QuestionAnswerAdvisor
        String answer = chatClient.prompt()
                .user(request.question())
                .call()
                .content();

        log.info("Respuesta generada. Procediendo a generación visual.");

        // Generación visual (Pilar de la Charla - "Haz que Spring AI te la dibuje")
        String imageUrl = imageGenerationService.generateImage(answer);

        return new QuestionResponse(
                request.question(),
                answer,
                List.of("Source: Spring AI VectorStore"),
                4,
                imageUrl
        );
    }
}
