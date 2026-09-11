package com.docucanvas.infrastructure.ai.image.gemini;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifica el contrato con la Gemini Developer API sin gastar cuota: se simula
 * el transporte HTTP y se comprueba lo que de verdad importa — que la petición
 * lleva el prompt y la clave donde deben ir, y que la respuesta se traduce al
 * modelo de Spring AI.
 */
@DisplayName("GeminiImageModel — contrato con la Developer API")
class GeminiImageModelTest {

    private static final String PROMPT = "clean vector infographic of a Kafka flow, no text";

    private GeminiImageProperties props() {
        return new GeminiImageProperties(true, "clave-de-prueba", "imagen-3.0-generate-002", 90);
    }

    @Test
    @DisplayName("Envía el prompt al endpoint del modelo y la clave por cabecera, no por URL")
    void enviaElPromptYLaClavePorCabecera() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/imagen-3.0-generate-002:predict"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                // La clave NO debe viajar como query param: acabaria en logs de acceso.
                .andExpect(header("x-goog-api-key", "clave-de-prueba"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.instances[0].prompt").value(PROMPT))
                .andExpect(jsonPath("$.parameters.sampleCount").value(1))
                .andExpect(jsonPath("$.parameters.aspectRatio").value("1:1"))
                .andRespond(withSuccess(
                        "{\"predictions\":[{\"bytesBase64Encoded\":\"QUJD\",\"mimeType\":\"image/png\"}]}",
                        MediaType.APPLICATION_JSON));

        GeminiImageModel model = new GeminiImageModel(props(), builder);

        ImageResponse response = model.call(new ImagePrompt(PROMPT,
                ImageOptionsBuilder.builder().width(1024).height(1024).build()));

        assertThat(response.getResult().getOutput().getB64Json()).isEqualTo("QUJD");
        server.verify();
    }

    @Test
    @DisplayName("Traduce dimensiones a la proporción más cercana, que es lo que acepta Imagen")
    void traduceDimensionesAProporcion() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(jsonPath("$.parameters.aspectRatio").value("16:9"))
                .andRespond(withSuccess("{\"predictions\":[{\"bytesBase64Encoded\":\"WA==\"}]}",
                        MediaType.APPLICATION_JSON));

        new GeminiImageModel(props(), builder).call(new ImagePrompt(PROMPT,
                ImageOptionsBuilder.builder().width(1920).height(1080).build()));

        server.verify();
    }

    @Test
    @DisplayName("Una respuesta sin imágenes no revienta: devuelve vacío y el pipeline degradará")
    void respuestaSinImagenesDevuelveVacio() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString(":predict")))
                .andRespond(withSuccess("{\"predictions\":[]}", MediaType.APPLICATION_JSON));

        ImageResponse response = new GeminiImageModel(props(), builder)
                .call(new ImagePrompt(PROMPT, ImageOptionsBuilder.builder().build()));

        assertThat(response.getResults()).isEmpty();
    }
}
