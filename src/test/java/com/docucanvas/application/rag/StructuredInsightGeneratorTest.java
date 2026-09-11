package com.docucanvas.application.rag;

import com.docucanvas.application.question.RagInsight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test 2 — El LLM produce un {@code visualPrompt} válido a partir del contexto
 * recuperado, y el sistema lo extrae sin depender de expresiones regulares.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StructuredInsightGenerator — visualPrompt derivado del contexto")
class StructuredInsightGeneratorTest {

    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;

    private final StructuredInsightGenerator generator = new StructuredInsightGenerator();
    private final ChatOptions options = ChatOptions.builder().model("test").build();

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    @DisplayName("Extrae answer y visualPrompt del JSON del modelo")
    void extraeVisualPromptDelJson() {
        when(callResponseSpec.content()).thenReturn(
                "{\"answer\": \"El servicio de conciliacion contrasta movimientos con el banco.\","
                + "\"visualPrompt\": \"clean vector infographic of a reconciliation service reading a Kafka queue, no text\","
                + "\"relations\": []}");

        RagInsight insight = generator.generate(chatClient, "Contexto: Kafka...", options);

        assertThat(insight.hasVisualPrompt()).isTrue();
        assertThat(insight.visualPrompt()).contains("reconciliation service");
        assertThat(insight.answer()).contains("conciliacion");
    }

    @Test
    @DisplayName("Las instrucciones de formato se anaden al prompt enviado al modelo")
    void anadeLasInstruccionesDeFormatoAlPrompt() {
        when(callResponseSpec.content()).thenReturn(
                "{\"answer\":\"ok\",\"visualPrompt\":\"diagram\",\"relations\":[]}");

        generator.generate(chatClient, "Contexto relevante del documento", options);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(requestSpec).user(promptCaptor.capture());
        String sent = promptCaptor.getValue();

        // El contexto recuperado viaja intacto, y el esquema lo genera Spring AI
        // desde el record: no hay formato escrito a mano que mantener.
        assertThat(sent).contains("Contexto relevante del documento");
        assertThat(sent).containsIgnoringCase("visualPrompt");
    }

    @Test
    @DisplayName("Si el modelo ignora el formato, se conserva su texto y visualPrompt queda nulo")
    void sinJsonValidoNoInventaPromptVisual() {
        when(callResponseSpec.content()).thenReturn("Una respuesta en prosa, sin JSON.");

        RagInsight insight = generator.generate(chatClient, "contexto", options);

        assertThat(insight.answer()).isEqualTo("Una respuesta en prosa, sin JSON.");
        assertThat(insight.hasVisualPrompt())
                .as("sin salida estructurada no se debe inventar un prompt visual")
                .isFalse();
    }
}
