package com.docucanvas.integration;

import com.docucanvas.infrastructure.ai.image.gemini.GeminiImageModel;
import com.docucanvas.infrastructure.ai.image.gemini.GeminiImageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba contra la Gemini Developer API REAL.
 *
 * <p><b>Se ejecuta solo si existe {@code GEMINI_API_KEY}.</b> Sin la variable
 * JUnit la omite en vez de fallar: una prueba que consume cuota de un servicio
 * externo no puede romper el build de quien no tiene clave, pero tampoco debe
 * desaparecer — es la única que demuestra que la integración funciona de verdad
 * y no solo contra un mock.
 *
 * <pre>
 * export GEMINI_API_KEY=...
 * ./mvnw -Pgemini test -Dtest=GeminiLiveIT
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
@DisplayName("Integración real — Gemini Developer API responde")
class GeminiLiveIT {

    private static final String BASE = "https://generativelanguage.googleapis.com";

    private String apiKey() {
        return System.getenv("GEMINI_API_KEY");
    }

    @Test
    @DisplayName("Diagnóstico: qué modelos admite esta clave")
    @SuppressWarnings("unchecked")
    void listaLosModelosDisponiblesParaLaClave() {
        Map<String, Object> body = RestClient.create().get()
                .uri(BASE + "/v1beta/models")
                .header("x-goog-api-key", apiKey())
                .retrieve()
                .body(Map.class);

        assertThat(body).containsKey("models");
        List<Map<String, Object>> models = (List<Map<String, Object>>) body.get("models");

        // Se imprime a propósito: saber qué modelos habilita la cuenta evita
        // elegir uno por conjetura y descubrir el 404 en mitad de la demo.
        models.stream()
                .map(m -> m.get("name") + "  " + m.get("supportedGenerationMethods"))
                .sorted()
                .forEach(System.out::println);

        assertThat(models).isNotEmpty();
    }

    @Test
    @DisplayName("El chat responde por la capa compatible con OpenAI")
    @SuppressWarnings("unchecked")
    void elChatRespondePorLaCapaCompatible() {
        Map<String, Object> body = RestClient.create().post()
                .uri(BASE + "/v1beta/openai/chat/completions")
                .header("Authorization", "Bearer " + apiKey())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", System.getenv().getOrDefault("GEMINI_CHAT_MODEL", "gemini-2.0-flash"),
                        "messages", List.of(Map.of("role", "user",
                                "content", "Responde solo con la palabra: OK"))))
                .retrieve()
                .body(Map.class);

        assertThat(body).containsKey("choices");
        System.out.println("Respuesta de Gemini: " + body.get("choices"));
    }

    @Test
    @DisplayName("La generación de imágenes devuelve una imagen utilizable")
    void laGeneracionDeImagenesDevuelveUnaImagen() {
        GeminiImageProperties props = new GeminiImageProperties(
                true, apiKey(),
                System.getenv().getOrDefault("GEMINI_IMAGE_MODEL", "imagen-3.0-generate-002"),
                90);

        ImageResponse response = new GeminiImageModel(props, RestClient.builder())
                .call(new ImagePrompt(
                        "clean minimal vector infographic of a payment reconciliation service "
                                + "reading events from a message queue, flat design, no text",
                        ImageOptionsBuilder.builder().width(1024).height(1024).build()));

        assertThat(response.getResults()).isNotEmpty();
        assertThat(response.getResult().getOutput().getB64Json()).isNotBlank();
        System.out.println("Imagen recibida: "
                + response.getResult().getOutput().getB64Json().length() + " chars base64");
    }
}
