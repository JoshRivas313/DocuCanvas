package com.docucanvas.application.service;

import com.docucanvas.application.port.out.ChunkReadPort;
import com.docucanvas.application.question.AnswerQuestionCommand;
import com.docucanvas.application.question.AnswerResult;
import com.docucanvas.application.question.ConfidenceLevel;
import com.docucanvas.application.rag.RagPromptFactory;
import com.docucanvas.application.rag.RagRetriever;
import com.docucanvas.application.rag.StructuredInsightGenerator;
import com.docucanvas.application.visual.VisualRenderingService;
import com.docucanvas.application.visual.VisualSource;
import com.docucanvas.infrastructure.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del pipeline RAG en {@link QuestionService}.
 *
 * <p>Cubre tanto la orquestación original (retrieval, citas, resiliencia ante
 * fallo de imagen) como las correcciones de la auditoría de rendimiento
 * (num_predict, timeout dinámico, regla honesta con piso en 0 documentos) y
 * las adiciones del informe RAG (confianza, conceptos, relaciones, página).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionService — Pipeline RAG del informe")
class QuestionServiceTest {

    @Mock private VectorStore vectorStore;
    @Mock private com.docucanvas.application.port.out.DiagramRenderPort diagramRenderPort;
    @Mock private ChunkReadPort chunkReadPort;
    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;

    private final ConceptExtractor conceptExtractor = new ConceptExtractor();

    /** Configuración con los mismos valores por defecto que declara RagProperties. */
    static RagProperties defaultRagProperties() {
        return new RagProperties(
                new RagProperties.Chunking(200, 50, 5, 10000, true),
                new RagProperties.Retrieval(5, 20, 0.3, 0.0),
                new RagProperties.Generation(400, 3.7, 25.0, 5.0, 15, 30, 150),
                new RagProperties.Visual(1024, 1024));
    }

    private QuestionService questionService;

    @BeforeEach
    void setUp() {
        lenient().when(chatClientBuilder.defaultSystem(any(String.class))).thenReturn(chatClientBuilder);
        lenient().when(chatClientBuilder.build()).thenReturn(chatClient);

        RagProperties ragProperties = defaultRagProperties();
        questionService = new QuestionService(
                chatClientBuilder,
                new RagRetriever(vectorStore, ragProperties),
                new RagPromptFactory(),
                new StructuredInsightGenerator(),
                new VisualRenderingService(java.util.Optional.empty(), diagramRenderPort),
                conceptExtractor, chunkReadPort,
                ragProperties,
                "llama3.2", 0.7);

        // Por defecto hay chunks indexados, salvo que un test lo sobreescriba.
        lenient().when(chunkReadPort.countAll()).thenReturn(100);
        lenient().when(chunkReadPort.countByDocument(anyString())).thenReturn(50);
    }

    @SuppressWarnings("unchecked")
    private void stubChatResponse(String answer) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(answer);
    }

    private org.springframework.ai.document.Document docWithScore(String text, String source, double distance) {
        return new org.springframework.ai.document.Document(text, Map.of("source", source, "distance", distance));
    }

    @Test
    @DisplayName("Debe retornar respuesta con texto e infografía cuando el pipeline funciona")
    void debeRetornarRespuestaCompletaConImagen() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                docWithScore("Spring AI facilita RAG con PGVector.", "doc-1", 0.42)));
        stubChatResponse("Spring AI facilita la integración de RAG con PGVector.");
        when(diagramRenderPort.renderDiagram(any(), any()))
                .thenReturn("data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=");

        AnswerResult response = questionService.answer(
                new AnswerQuestionCommand("¿Qué es Spring AI?", 4, null));

        assertThat(response.answer()).contains("Spring AI");
        assertThat(response.imageUrl()).startsWith("data:image/svg+xml;base64,");
        assertThat(response.citations()).isNotEmpty();
        verify(diagramRenderPort, times(1)).renderDiagram(any(), any());
    }

    @Test
    @DisplayName("Debe incluir la fuente y el score en las citas cuando se filtra por documento")
    void debeIncluirFuenteEnCitasCuandoSeFiltraPorDocumento() {
        UUID documentId = UUID.randomUUID();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                docWithScore("contexto test", documentId.toString(), 0.4)));
        stubChatResponse("Respuesta filtrada por documento.");
        when(diagramRenderPort.renderDiagram(any(), any())).thenReturn("data:image/svg+xml;base64,AAAA");

        AnswerResult response = questionService.answer(
                new AnswerQuestionCommand("¿Qué dice el documento?", 3, documentId));

        assertThat(response.citations()).anyMatch(c -> c.source().contains(documentId.toString()));
        assertThat(response.citations().get(0).score()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.001));
        verify(chunkReadPort).countByDocument(documentId.toString());
        verify(chunkReadPort, never()).countAll();
    }

    @Test
    @DisplayName("Debe devolver la respuesta de texto aunque falle la generación de la infografía (resiliencia)")
    void debeResponderTextoAunqueFalleLaImagen() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                docWithScore("contexto fallo image", "doc-error", 0.45)));
        stubChatResponse("Respuesta de emergencia.");
        when(diagramRenderPort.renderDiagram(any(), any()))
                .thenThrow(new RuntimeException("Fallo generando SVG"));

        AnswerResult response = questionService.answer(
                new AnswerQuestionCommand("¿Qué es RAG?", 4, null));

        assertThat(response.answer()).isNotBlank();
        assertThat(response.imageUrl()).isEmpty();
    }

    @Test
    @DisplayName("Con 0 chunks indexados en el alcance, responde honestamente SIN invocar al LLM")
    void sinChunksIndexadosRespondeHonestoSinLlamarAlLlm() {
        when(chunkReadPort.countAll()).thenReturn(0);

        AnswerResult response = questionService.answer(
                new AnswerQuestionCommand("¿Qué dice el documento?", 5, null));

        assertThat(response.answer()).containsIgnoringCase("no hay documentos indexados");
        assertThat(response.citations()).isEmpty();
        assertThat(response.insights().totalChunksAnalyzed()).isZero();
        verifyNoInteractions(vectorStore, diagramRenderPort);
        verify(chatClient, never()).prompt();
    }

    @Test
    @DisplayName("La salida estructurada aporta respuesta, prompt visual y relaciones en una sola llamada")
    void salidaEstructuradaAportaRespuestaPromptVisualYRelaciones() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                docWithScore("Los gatos son felinos domésticos muy independientes", "doc-1", 0.4)));
        stubChatResponse("""
                {
                  "answer": "Los gatos son animales domésticos independientes.",
                  "visualPrompt": "clean vector infographic of domestic feline behaviour, no text",
                  "relations": [
                    {"concept": "Felinos", "relatedTopics": ["comportamiento", "domesticación"]},
                    {"concept": "Independientes", "relatedTopics": ["autonomía", "carácter"]}
                  ]
                }
                """);
        when(diagramRenderPort.renderDiagram(any(), any())).thenReturn("");

        AnswerResult response = questionService.answer(
                new AnswerQuestionCommand("¿Cómo son los gatos?", 5, null));

        assertThat(response.answer()).isEqualTo("Los gatos son animales domésticos independientes.");
        assertThat(response.visualPrompt()).contains("no text");
        assertThat(response.insights().conceptRelations()).hasSize(2);
        assertThat(response.insights().conceptRelations().get(0).concept()).isEqualTo("Felinos");
        assertThat(response.insights().conceptRelations().get(0).relatedTopics())
                .containsExactly("comportamiento", "domesticación");
    }

    @Test
    @DisplayName("Si el modelo no devuelve JSON válido, se conserva su texto como respuesta en vez de fallar")
    void jsonInvalidoDegradaATextoPlanoSinFallar() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                docWithScore("contexto", "doc-1", 0.4)));
        // Un modelo pequeño en local ignora el formato con frecuencia: es el caso normal, no el raro.
        stubChatResponse("Una respuesta normal, en prosa, sin nada de JSON.");
        when(diagramRenderPort.renderDiagram(any(), any())).thenReturn("");

        AnswerResult response = questionService.answer(
                new AnswerQuestionCommand("pregunta", 5, null));

        assertThat(response.answer()).isEqualTo("Una respuesta normal, en prosa, sin nada de JSON.");
        assertThat(response.visualPrompt()).isNull();
        assertThat(response.insights().conceptRelations()).isEmpty();
    }

    @Test
    @DisplayName("El JSON envuelto en vallas de bloque de código se parsea igualmente")
    void jsonEnvueltoEnVallasDeCodigoSeParsea() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                docWithScore("contexto", "doc-1", 0.4)));
        stubChatResponse("""
                ```json
                {"answer": "Respuesta envuelta.", "visualPrompt": "vector diagram, no text", "relations": []}
                ```
                """);
        when(diagramRenderPort.renderDiagram(any(), any())).thenReturn("");

        AnswerResult response = questionService.answer(
                new AnswerQuestionCommand("pregunta", 5, null));

        assertThat(response.answer()).isEqualTo("Respuesta envuelta.");
        assertThat(response.visualPrompt()).isEqualTo("vector diagram, no text");
    }

    @Test
    @DisplayName("La confianza ALTA/MEDIA/BAJA se deriva del score promedio real de las citas")
    void confianzaSeDerivaDelScorePromedio() {
        // distance=0.35 -> similarity=0.65 (ALTA, >=0.55)
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                docWithScore("contexto relevante", "doc-1", 0.35)));
        stubChatResponse("Respuesta con alta confianza.");
        when(diagramRenderPort.renderDiagram(any(), any())).thenReturn("");

        AnswerResult response = questionService.answer(new AnswerQuestionCommand("pregunta", 5, null));

        assertThat(response.insights().confidenceLevel()).isEqualTo(ConfidenceLevel.ALTA);
        assertThat(response.insights().partialMatch()).isFalse();
    }

    @Test
    @DisplayName("Score promedio bajo produce confianza BAJA y marca partialMatch=true")
    void scorePromedioBajoProduceConfianzaBaja() {
        // distance=0.75 -> similarity=0.25 (BAJA, <0.40)
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                docWithScore("contexto tangencial", "doc-1", 0.75)));
        stubChatResponse("La documentación contiene información relacionada, aunque no responde de forma explícita la pregunta.");
        when(diagramRenderPort.renderDiagram(any(), any())).thenReturn("");

        AnswerResult response = questionService.answer(new AnswerQuestionCommand("pregunta", 5, null));

        assertThat(response.insights().confidenceLevel()).isEqualTo(ConfidenceLevel.BAJA);
        assertThat(response.insights().partialMatch()).isTrue();
    }

    @Test
    @DisplayName("La página del chunk (metadata 'page') se propaga a la citación")
    void laPaginaSePropagaALaCitacion() {
        org.springframework.ai.document.Document doc = new org.springframework.ai.document.Document(
                "contenido", Map.of("source", "doc.pdf", "distance", 0.4, "page", 7));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        stubChatResponse("Respuesta.");
        when(diagramRenderPort.renderDiagram(any(), any())).thenReturn("");

        AnswerResult response = questionService.answer(new AnswerQuestionCommand("pregunta", 5, null));

        assertThat(response.citations().get(0).page()).isEqualTo(7);
    }

    @Test
    @DisplayName("Chunks sin página en metadata (formatos sin paginación) devuelven page=null, no un valor inventado")
    void sinPaginaEnMetadataDevuelveNull() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                docWithScore("contenido de un TXT", "doc.txt", 0.4)));
        stubChatResponse("Respuesta.");
        when(diagramRenderPort.renderDiagram(any(), any())).thenReturn("");

        AnswerResult response = questionService.answer(new AnswerQuestionCommand("pregunta", 5, null));

        assertThat(response.citations().get(0).page()).isNull();
    }

    @Test
    @DisplayName("Cada llamada al LLM fija maxTokens para acotar la longitud de salida")
    void fijaMaxTokensEnCadaLlamada() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                docWithScore("contexto", "doc-1", 0.4)));
        stubChatResponse("Respuesta.");
        when(diagramRenderPort.renderDiagram(any(), any())).thenReturn("");

        questionService.answer(new AnswerQuestionCommand("pregunta", 5, null));

        ArgumentCaptor<ChatOptions> optionsCaptor = ArgumentCaptor.forClass(ChatOptions.class);
        verify(requestSpec).options(optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getMaxTokens()).isNotNull().isPositive();
    }

    @Test
    @DisplayName("Las opciones enviadas son portables (ChatOptions), no específicas de Ollama")
    void usaOpcionesPortablesNoEspecificasDelProveedor() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                docWithScore("contexto", "doc-1", 0.4)));
        stubChatResponse("Respuesta.");
        when(diagramRenderPort.renderDiagram(any(), any())).thenReturn("");

        questionService.answer(new AnswerQuestionCommand("pregunta", 5, null));

        ArgumentCaptor<ChatOptions> optionsCaptor = ArgumentCaptor.forClass(ChatOptions.class);
        verify(requestSpec).options(optionsCaptor.capture());
        ChatOptions options = optionsCaptor.getValue();
        // Es lo que permite cambiar Ollama -> Gemini sin tocar esta clase.
        assertThat(options).isNotInstanceOf(org.springframework.ai.ollama.api.OllamaOptions.class);
        assertThat(options.getModel()).isEqualTo("llama3.2");
        assertThat(options.getTemperature()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("topK por encima del máximo configurado se recorta en vez de llegar al VectorStore")
    void topKExcesivoSeRecortaAlMaximoConfigurado() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                docWithScore("contexto", "doc-1", 0.4)));
        stubChatResponse("Respuesta.");
        when(diagramRenderPort.renderDiagram(any(), any())).thenReturn("");

        questionService.answer(new AnswerQuestionCommand("pregunta", 5000, null));

        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(searchCaptor.capture());
        assertThat(searchCaptor.getValue().getTopK()).isEqualTo(20); // max-top-k configurado
    }

    @Test
    @DisplayName("Si el umbral estricto no devuelve nada se reintenta relajado y se declara en los insights")
    void reintentaConUmbralRelajadoYLoDeclara() {
        // Primera búsqueda (umbral 0.3) vacía; segunda (0.0) devuelve evidencia débil.
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(docWithScore("contexto tangencial", "doc-1", 0.8)));
        stubChatResponse("Respuesta con evidencia débil.");
        when(diagramRenderPort.renderDiagram(any(), any())).thenReturn("");

        AnswerResult response = questionService.answer(
                new AnswerQuestionCommand("¿De qué trata todo esto?", 5, null));

        verify(vectorStore, times(2)).similaritySearch(any(SearchRequest.class));
        assertThat(response.citations()).hasSize(1);
        assertThat(response.insights().howItWasFound())
                .contains("umbral de similitud habitual");
    }
}
