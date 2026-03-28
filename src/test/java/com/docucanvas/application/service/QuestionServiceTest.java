package com.docucanvas.application.service;

import com.docucanvas.api.dto.request.QuestionRequest;
import com.docucanvas.api.dto.response.QuestionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.Image;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del pipeline RAG en QuestionService.
 *
 * <p>Usa Mockito para simular ChatClient e ImageModel, garantizando
 * que no se consumen tokens de OpenAI en la suite de tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionService — Pipeline RAG unitario")
class QuestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ImageGenerationService imageGenerationService;

    @Mock
    private ImageModel imageModel;

    @Mock
    private org.springframework.jdbc.core.simple.JdbcClient jdbcClient;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ImageResponse imageResponse;

    @Mock
    private ImageGeneration imageGeneration;

    @Mock
    private Image image;

    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        // Encadenamos el builder de ChatClient para que siempre devuelva nuestro mock

        lenient().when(chatClientBuilder.defaultSystem(any(String.class))).thenReturn(chatClientBuilder);
        lenient().when(chatClientBuilder.build()).thenReturn(chatClient);

        questionService = new QuestionService(
                chatClientBuilder, vectorStore, imageGenerationService, imageModel, jdbcClient);
    }

    @Test
    @DisplayName("Debe retornar respuesta con texto e imagen cuando el pipeline RAG funciona correctamente")
    void debeRetornarRespuestaCompletaConImagenCuandoPipelineEsExitoso() {
        // Arrange — simulamos respuesta del LLM y VectorStore
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(java.util.List.of(
                new org.springframework.ai.document.Document("contexto dummy", java.util.Map.of("source", "doc-1"))));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Spring AI facilita la integración de RAG con PGVector.");

        // Arrange — simulamos respuesta de ImageModel
        when(imageGenerationService.generateImagePromptFromContext(any(), any())).thenReturn(new ImagePrompt("test prompt"));
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(imageResponse);
        when(imageResponse.getResult()).thenReturn(imageGeneration);
        when(imageGeneration.getOutput()).thenReturn(image);
        when(image.getUrl()).thenReturn("https://dalle.example.com/img/rag.png");

        // Act
        QuestionResponse response = questionService.answer(
                new QuestionRequest("¿Qué es Spring AI?", 4, null));

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.answer()).contains("Spring AI");
        assertThat(response.imageUrl()).isEqualTo("https://dalle.example.com/img/rag.png");
        assertThat(response.citations()).isNotEmpty();
        verify(imageModel, times(1)).call(any(ImagePrompt.class));
    }

    @Test
    @DisplayName("Debe incluir el documentId en las fuentes cuando se filtra por documento")
    void debeIncluirDocumentIdEnFuentesCuandoSeFiltraPorDocumento() {
        // Arrange
        UUID documentId = UUID.randomUUID();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(java.util.List.of(
                new org.springframework.ai.document.Document("contexto test", java.util.Map.of("source", documentId.toString()))));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Respuesta filtrada por documento.");

        when(imageGenerationService.generateImagePromptFromContext(any(), any())).thenReturn(new ImagePrompt("test"));
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(imageResponse);
        when(imageResponse.getResult()).thenReturn(imageGeneration);
        when(imageGeneration.getOutput()).thenReturn(image);
        when(image.getUrl()).thenReturn("https://dalle.example.com/img/doc.png");

        // Act
        QuestionResponse response = questionService.answer(
                new QuestionRequest("¿Qué dice el documento?", 3, documentId));

        // Assert
        assertThat(response.citations()).anyMatch(c -> c.source().contains(documentId.toString()));
    }

    @Test
    @DisplayName("Debe devolver respuesta texto aunque ImageModel falle (resiliencia)")
    void debeResponderTextoAunqueFalleImageModel() {
        // Arrange
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(java.util.List.of(
                new org.springframework.ai.document.Document("contexto fallo image", java.util.Map.of("source", "doc-error"))));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Respuesta de emergencia.");

        when(imageGenerationService.generateImagePromptFromContext(any(), any())).thenReturn(new ImagePrompt("test"));
        when(imageModel.call(any(ImagePrompt.class)))
                .thenThrow(new RuntimeException("Cuota de DALL-E agotada"));

        // Act
        QuestionResponse response = questionService.answer(
                new QuestionRequest("¿Qué es RAG?", 4, null));

        // Assert
        assertThat(response.answer()).isNotBlank();
        assertThat(response.imageUrl()).isEmpty();
    }
}

