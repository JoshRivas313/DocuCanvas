package com.docucanvas.application.service;

import com.docucanvas.api.dto.request.QuestionRequest;
import com.docucanvas.api.dto.response.QuestionResponse;
import com.docucanvas.domain.model.DocumentChunk;
import com.docucanvas.domain.repository.ChunkRepository;
import com.docucanvas.infrastructure.ai.GeminiEmbeddingAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatClient chatClient;
    @Mock
    private ChatClient.PromptRoutedSpec promptRoutedSpec;
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private GeminiEmbeddingAdapter embeddingAdapter;

    @Mock
    private ChunkRepository chunkRepository;

    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        questionService = new QuestionService(chatClientBuilder, embeddingAdapter, chunkRepository);
    }

    @Test
    void shouldAnswerQuestionSuccessfully() {
        // Arrange
        String questionText = "¿Qué es DocuCanvas?";
        QuestionRequest request = new QuestionRequest(questionText);
        
        float[] fakeEmbedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingAdapter.embed(questionText)).thenReturn(fakeEmbedding);

        DocumentChunk chunk1 = new DocumentChunk(
                UUID.randomUUID(), UUID.randomUUID(), 0, "DocuCanvas es una plataforma de RAG.", fakeEmbedding, "{}", Instant.now());
        when(chunkRepository.findSimilarChunks(fakeEmbedding, 5)).thenReturn(List.of(chunk1));

        when(chatClient.prompt()).thenReturn(promptRoutedSpec);
        when(promptRoutedSpec.user(anyString())).thenReturn(promptRoutedSpec);
        when(promptRoutedSpec.system(anyString())).thenReturn(promptRoutedSpec);
        when(promptRoutedSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("DocuCanvas es una plataforma RAG, según el contexto.");

        // Act
        QuestionResponse response = questionService.askQuestion(request);

        // Assert
        assertNotNull(response);
        assertEquals(questionText, response.question());
        assertEquals("DocuCanvas es una plataforma RAG, según el contexto.", response.answer());
        assertEquals(1, response.sources().size());
        
        Mockito.verify(embeddingAdapter).embed(questionText);
        Mockito.verify(chunkRepository).findSimilarChunks(fakeEmbedding, 5);
    }
}
