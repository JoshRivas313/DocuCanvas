package com.docucanvas.application.service;

import com.docucanvas.application.question.AnswerQuestionCommand;
import com.docucanvas.application.question.AnswerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del pipeline RAG en {@link QuestionService}.
 *
 * <p>Simula {@link ChatClient} (Ollama) y {@link ImageGenerationService}
 * (infografía SVG local) para verificar la orquestación sin invocar servicios
 * externos. Refleja la arquitectura actual: Ollama para el LLM y generación de
 * SVG local (sin DALL-E ni {@code ImageModel}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionService — Pipeline RAG unitario")
class QuestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ImageGenerationService imageGenerationService;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        // El servicio construye el ChatClient con builder.defaultSystem(...).build()
        lenient().when(chatClientBuilder.defaultSystem(any(String.class))).thenReturn(chatClientBuilder);
        lenient().when(chatClientBuilder.build()).thenReturn(chatClient);

        questionService = new QuestionService(chatClientBuilder, vectorStore, imageGenerationService);
    }

    @SuppressWarnings("unchecked")
    private void stubChatResponse(String answer) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(answer);
    }

    @Test
    @DisplayName("Debe retornar respuesta con texto e infografía SVG cuando el pipeline funciona")
    void debeRetornarRespuestaCompletaConImagen() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(java.util.List.of(
                new org.springframework.ai.document.Document("contexto dummy", java.util.Map.of("source", "doc-1"))));
        stubChatResponse("Spring AI facilita la integración de RAG con PGVector.");
        when(imageGenerationService.generateImageDataUrl(any(), any()))
                .thenReturn("data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=");

        AnswerResult response = questionService.answer(
                new AnswerQuestionCommand("¿Qué es Spring AI?", 4, null));

        assertThat(response).isNotNull();
        assertThat(response.answer()).contains("Spring AI");
        assertThat(response.imageUrl()).startsWith("data:image/svg+xml;base64,");
        assertThat(response.citations()).isNotEmpty();
        verify(imageGenerationService, times(1)).generateImageDataUrl(any(), any());
    }

    @Test
    @DisplayName("Debe incluir la fuente del documento en las citas cuando se filtra por documento")
    void debeIncluirFuenteEnCitasCuandoSeFiltraPorDocumento() {
        UUID documentId = UUID.randomUUID();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(java.util.List.of(
                new org.springframework.ai.document.Document("contexto test", java.util.Map.of("source", documentId.toString()))));
        stubChatResponse("Respuesta filtrada por documento.");
        when(imageGenerationService.generateImageDataUrl(any(), any())).thenReturn("data:image/svg+xml;base64,AAAA");

        AnswerResult response = questionService.answer(
                new AnswerQuestionCommand("¿Qué dice el documento?", 3, documentId));

        assertThat(response.citations()).anyMatch(c -> c.source().contains(documentId.toString()));
    }

    @Test
    @DisplayName("Debe devolver la respuesta de texto aunque falle la generación de la infografía (resiliencia)")
    void debeResponderTextoAunqueFalleLaImagen() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(java.util.List.of(
                new org.springframework.ai.document.Document("contexto fallo image", java.util.Map.of("source", "doc-error"))));
        stubChatResponse("Respuesta de emergencia.");
        when(imageGenerationService.generateImageDataUrl(any(), any()))
                .thenThrow(new RuntimeException("Fallo generando SVG"));

        AnswerResult response = questionService.answer(
                new AnswerQuestionCommand("¿Qué es RAG?", 4, null));

        assertThat(response.answer()).isNotBlank();
        assertThat(response.imageUrl()).isEmpty();
    }
}
