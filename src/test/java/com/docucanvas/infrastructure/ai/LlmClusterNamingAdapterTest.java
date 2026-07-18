package com.docucanvas.infrastructure.ai;

import com.docucanvas.application.chunk.ClusterName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests de {@link LlmClusterNamingAdapter}, centrados en los invariantes del
 * cache por firma de contenido introducido en el Sprint 2.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmClusterNamingAdapter — Naming con cache por firma")
class LlmClusterNamingAdapterTest {

    @Mock private ChatClient.Builder builder;
    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;

    private LlmClusterNamingAdapter adapter;

    @BeforeEach
    void setUp() {
        when(builder.build()).thenReturn(chatClient);
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
        adapter = new LlmClusterNamingAdapter(builder);
    }

    private void stubLlm(String content) {
        when(callResponseSpec.content()).thenReturn(content);
    }

    @Test
    @DisplayName("Contenido idéntico en 2 llamadas invoca al LLM una sola vez (cache hit)")
    void cacheHitPorFirma() {
        stubLlm("Nombre: Felinos\nDescripción: Sobre gatos domésticos.");
        List<String> contents = List.of("Los gatos son animales domésticos muy independientes");

        ClusterName first = adapter.name(0, contents);
        ClusterName second = adapter.name(0, contents);

        verify(chatClient, times(1)).prompt(); // segunda vez desde cache
        assertThat(first).isEqualTo(second);
        assertThat(first.name()).isEqualTo("Grupo 0: Felinos");
    }

    @Test
    @DisplayName("Mismo contenido con índice distinto reutiliza el cache pero recompone el prefijo del grupo")
    void mismoContenidoIndiceDistinto() {
        stubLlm("Nombre: Felinos\nDescripción: Sobre gatos.");
        List<String> contents = List.of("Los gatos son animales domésticos");

        ClusterName g0 = adapter.name(0, contents);
        ClusterName g1 = adapter.name(1, contents);

        verify(chatClient, times(1)).prompt(); // el LLM solo se llamó una vez
        assertThat(g0.name()).isEqualTo("Grupo 0: Felinos");
        assertThat(g1.name()).isEqualTo("Grupo 1: Felinos");
        assertThat(g0.description()).isEqualTo(g1.description());
    }

    @Test
    @DisplayName("El fallback (fallo del LLM) NO se cachea: una llamada posterior reintenta el LLM")
    void fallbackNoSeCachea() {
        // 1ª llamada: el LLM falla → fallback heurístico (no cacheado)
        // 2ª llamada: el LLM responde → nombre del LLM
        when(callResponseSpec.content())
                .thenThrow(new RuntimeException("Ollama caído"))
                .thenReturn("Nombre: Felinos\nDescripción: Sobre gatos.");
        List<String> contents = List.of("Los gatos domésticos duermen mucho durante el día");

        ClusterName conFallo = adapter.name(0, contents);
        ClusterName conExito = adapter.name(0, contents);

        verify(chatClient, times(2)).prompt(); // reintentó porque el fallback no se cacheó
        assertThat(conFallo.name()).doesNotContain("Felinos"); // heurística: palabra más larga
        assertThat(conExito.name()).isEqualTo("Grupo 0: Felinos");
    }

    @Test
    @DisplayName("Contenido vacío no invoca al LLM y devuelve un nombre placeholder")
    void contenidoVacioNoLlamaLlm() {
        ClusterName result = adapter.name(3, List.of());

        verify(chatClient, never()).prompt();
        assertThat(result.name()).isEqualTo("Grupo 3");
        assertThat(result.description()).isEqualTo("Sin contenido");
    }
}
