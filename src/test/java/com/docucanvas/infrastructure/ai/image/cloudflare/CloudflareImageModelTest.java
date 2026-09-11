package com.docucanvas.infrastructure.ai.image.cloudflare;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Contrato con Workers AI, sin gastar Neurons. */
@DisplayName("CloudflareImageModel — contrato con Workers AI")
class CloudflareImageModelTest {

    private static final String PROMPT = "clean vector infographic of a Kafka flow, no text";

    private CloudflareImageProperties props() {
        return new CloudflareImageProperties(
                true, "cuenta-de-prueba", "token-de-prueba", "@cf/black-forest-labs/flux-1-schnell", 4);
    }

    @Test
    @DisplayName("Envía el prompt al endpoint de la cuenta con el token como Bearer")
    void enviaElPromptConElTokenBearer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.cloudflare.com/client/v4/accounts/cuenta-de-prueba"
                        + "/ai/run/@cf/black-forest-labs/flux-1-schnell"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-de-prueba"))
                .andExpect(jsonPath("$.prompt").value(PROMPT))
                .andExpect(jsonPath("$.steps").value(4))
                .andRespond(withSuccess(
                        "{\"result\":{\"image\":\"QUJD\"},\"success\":true,\"errors\":[]}",
                        MediaType.APPLICATION_JSON));

        ImageResponse response = new CloudflareImageModel(props(), builder)
                .call(new ImagePrompt(PROMPT, ImageOptionsBuilder.builder().width(1024).height(1024).build()));

        assertThat(response.getResult().getOutput().getB64Json()).isEqualTo("QUJD");
        server.verify();
    }

    @Test
    @DisplayName("Una respuesta con errores no revienta: devuelve vacío y el pipeline degradará al SVG")
    void respuestaConErroresDegrada() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/ai/run/")))
                .andRespond(withSuccess(
                        "{\"result\":null,\"success\":false,\"errors\":[{\"message\":\"sin neurons\"}]}",
                        MediaType.APPLICATION_JSON));

        ImageResponse response = new CloudflareImageModel(props(), builder)
                .call(new ImagePrompt(PROMPT, ImageOptionsBuilder.builder().build()));

        assertThat(response.getResults()).isEmpty();
    }
}
