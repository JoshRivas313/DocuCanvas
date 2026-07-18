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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests de {@link LlmClusterNamingAdapter}: el contrato por lote (una sola
 * llamada al LLM para todos los clusters pendientes) y los invariantes del
 * cache por firma de contenido.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmClusterNamingAdapter — Naming por lote con cache por firma")
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
    @DisplayName("k clusters pendientes se resuelven con una sola llamada al LLM")
    void kClustersEnUnaSolaLlamada() {
        stubLlm("""
                Cluster 0:
                Nombre: Felinos
                Descripción: Sobre gatos domésticos.

                Cluster 1:
                Nombre: Cafe
                Descripción: Sobre el proceso de tostado.
                """);
        Map<Integer, List<String>> clusters = Map.of(
                0, List.of("Los gatos son animales domésticos"),
                1, List.of("El café arábica se cultiva en altitud"));

        Map<Integer, ClusterName> result = adapter.nameAll(clusters);

        verify(chatClient, times(1)).prompt(); // una sola llamada para ambos clusters
        assertThat(result.get(0).name()).isEqualTo("Grupo 0: Felinos");
        assertThat(result.get(1).name()).isEqualTo("Grupo 1: Cafe");
    }

    @Test
    @DisplayName("Contenido ya cacheado no vuelve a llamar al LLM; solo se piden los clusters pendientes")
    void reutilizaCacheEntreLotes() {
        stubLlm("Cluster 0:\nNombre: Felinos\nDescripción: Sobre gatos.\n");
        List<String> contents = List.of("Los gatos son animales domésticos");

        adapter.nameAll(Map.of(0, contents)); // 1ª vez: llama al LLM y cachea

        // Sin re-stubear: si el cache no funcionara, esta 2ª llamada intentaría
        // volver a invocar al LLM con la respuesta ya consumida y fallaría.
        Map<Integer, ClusterName> second = adapter.nameAll(Map.of(5, contents)); // mismo contenido, índice distinto

        // el LLM se llamó 1 vez en total: la 2ª resolución vino del cache por firma
        verify(chatClient, times(1)).prompt();
        assertThat(second.get(5).name()).isEqualTo("Grupo 5: Felinos");
    }

    @Test
    @DisplayName("Si el LLM falla, cada cluster pendiente cae en el fallback heurístico sin lanzar excepción")
    void fallbackAnteFalloDelLlm() {
        when(callResponseSpec.content()).thenThrow(new RuntimeException("Ollama caído"));
        Map<Integer, List<String>> clusters = Map.of(
                0, List.of("Los gatos domésticos duermen mucho durante el día"));

        Map<Integer, ClusterName> result = adapter.nameAll(clusters);

        assertThat(result.get(0)).isNotNull();
        assertThat(result.get(0).name()).startsWith("Grupo 0:");
    }

    @Test
    @DisplayName("El fallback NO se cachea: una llamada posterior con el mismo contenido reintenta el LLM")
    void fallbackNoSeCachea() {
        when(callResponseSpec.content())
                .thenThrow(new RuntimeException("Ollama caído"))
                .thenReturn("Cluster 0:\nNombre: Felinos\nDescripción: Sobre gatos.\n");
        List<String> contents = List.of("Los gatos domésticos duermen mucho durante el día");

        Map<Integer, ClusterName> conFallo = adapter.nameAll(Map.of(0, contents));
        Map<Integer, ClusterName> conExito = adapter.nameAll(Map.of(0, contents));

        verify(chatClient, times(2)).prompt(); // reintentó porque el fallback no se cacheó
        assertThat(conFallo.get(0).name()).doesNotContain("Felinos");
        assertThat(conExito.get(0).name()).isEqualTo("Grupo 0: Felinos");
    }

    @Test
    @DisplayName("Clusters sin contenido no se incluyen en la llamada al LLM y devuelven un nombre placeholder")
    void clusterVacioNoSeEnviaAlLlm() {
        stubLlm("Cluster 0:\nNombre: Felinos\nDescripción: Sobre gatos.\n");
        Map<Integer, List<String>> clusters = Map.of(
                0, List.of("Los gatos son animales domésticos"),
                3, List.of());

        Map<Integer, ClusterName> result = adapter.nameAll(clusters);

        assertThat(result.get(3).name()).isEqualTo("Grupo 3");
        assertThat(result.get(3).description()).isEqualTo("Sin contenido");
    }

    @Test
    @DisplayName("Si todos los clusters están vacíos, no se invoca al LLM en absoluto")
    void todosVaciosNoLlamaLlm() {
        Map<Integer, ClusterName> result = adapter.nameAll(Map.of(0, List.of(), 1, List.of()));

        verify(chatClient, never()).prompt();
        assertThat(result).hasSize(2);
    }
}
