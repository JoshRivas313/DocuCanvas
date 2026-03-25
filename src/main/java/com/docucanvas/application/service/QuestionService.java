package com.docucanvas.application.service;

import com.docucanvas.api.dto.request.QuestionRequest;
import com.docucanvas.api.dto.response.QuestionResponse;
import com.docucanvas.domain.model.DocumentChunk;
import com.docucanvas.domain.repository.ChunkRepository;
import com.docucanvas.infrastructure.ai.GeminiEmbeddingAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.docucanvas.infrastructure.ai.GeminiRestClient;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class QuestionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QuestionService.class);
    private static final String RAG_PROMPT_TEMPLATE = """
            Eres un asistente experto en análisis de documentos.
            Responde a la pregunta del usuario usando ÚNICAMENTE la información del contexto proporcionado.
            Si la información no es suficiente para responder, indícalo claramente.
            
            Contexto de los documentos:
            ---
            %s
            ---
            
            Pregunta del usuario: %s
            
            Respuesta:
            """;

    private final ChunkRepository chunkRepository;
    private final GeminiEmbeddingAdapter embeddingAdapter;
    private final GeminiRestClient geminiRestClient;
    private final ImageGenerationService imageGenerationService;

    public QuestionService(ChunkRepository chunkRepository,
                           GeminiEmbeddingAdapter embeddingAdapter,
                           GeminiRestClient geminiRestClient,
                           ImageGenerationService imageGenerationService) {
        this.chunkRepository = chunkRepository;
        this.embeddingAdapter = embeddingAdapter;
        this.geminiRestClient = geminiRestClient;
        this.imageGenerationService = imageGenerationService;
    }

    public QuestionResponse answer(QuestionRequest request) {
        log.info("Procesando pregunta: {}", request.question());

        // 1. Generar embedding de la pregunta
        float[] questionEmbedding = embeddingAdapter.embed(request.question());

        // 2. Recuperar chunks relevantes (similitud vectorial)
        List<DocumentChunk> relevantChunks = chunkRepository.findSimilar(
                questionEmbedding, request.maxChunks());

        if (relevantChunks.isEmpty()) {
            log.warn("No se encontraron chunks relevantes para la pregunta");
            return new QuestionResponse(
                    request.question(),
                    "No se encontró información relevante en los documentos indexados.",
                    List.of(),
                    0,
                    null
            );
        }

        // 3. Construir contexto desde los chunks
        String context = relevantChunks.stream()
                .map(DocumentChunk::content)
                .reduce("", (a, b) -> a + "\n\n" + b)
                .trim();

        // 4. Llamar al LLM con RAG prompt
        String prompt = RAG_PROMPT_TEMPLATE.formatted(context, request.question());
        String answer = geminiRestClient.generate(prompt);

        // 5. Extraer fuentes únicas (por documentId)
        List<String> sources = relevantChunks.stream()
                .map(chunk -> chunk.documentId().toString())
                .distinct()
                .toList();

        log.info("Respuesta generada con {} chunks de contexto", relevantChunks.size());

        // 6. Generar imagen visual representando el conocimiento (Pilar de la Charla)
        String imageUrl = imageGenerationService.generateImage(answer);

        return new QuestionResponse(
                request.question(),
                answer,
                sources,
                relevantChunks.size(),
                imageUrl
        );
    }
}
