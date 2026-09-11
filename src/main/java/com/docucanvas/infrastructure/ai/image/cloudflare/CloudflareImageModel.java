package com.docucanvas.infrastructure.ai.image.cloudflare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link ImageModel} sobre Workers AI de Cloudflare (FLUX-1-schnell).
 *
 * <p><b>Es la segunda implementacion de la misma interfaz</b>, junto a
 * {@code GeminiImageModel}. Esa es justo la demostracion que vale la pena
 * ensenar: el pipeline no sabe cual esta enchufado.
 * {@code SpringAiImageModelAdapter} depende de {@link ImageModel} y no cambia
 * ni una linea al cambiar de proveedor; lo unico que decide cual se usa es una
 * propiedad de configuracion.
 *
 * <p>Motivo practico de su existencia: la generacion de imagen de Gemini no
 * tiene nivel gratuito, y esta si.
 */
public class CloudflareImageModel implements ImageModel {

    private static final Logger log = LoggerFactory.getLogger(CloudflareImageModel.class);

    private static final String BASE_URL = "https://api.cloudflare.com/client/v4";

    private final CloudflareImageProperties config;
    private final RestClient restClient;

    public CloudflareImageModel(CloudflareImageProperties config, RestClient.Builder restClientBuilder) {
        this.config = config;
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
    }

    @Override
    public ImageResponse call(ImagePrompt request) {
        String prompt = request.getInstructions().get(0).getText();
        log.info("[IMAGE] Cloudflare Workers AI ({}) generando imagen", config.model());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                // El nombre del modelo lleva "@" y barras que son separadores de ruta
                // reales. Pasarlo como variable de plantilla lo URL-codifica
                // (%40cf%2F...) y Cloudflare responde 404, asi que se concatena a la
                // ruta y solo accountId viaja como variable.
                .uri("/accounts/{accountId}/ai/run/" + config.model(), config.accountId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("prompt", prompt, "steps", config.steps()))
                .retrieve()
                .body(Map.class);

        return toImageResponse(response);
    }

    private ImageResponse toImageResponse(Map<String, Object> response) {
        List<ImageGeneration> generations = new ArrayList<>();
        if (response == null) {
            return new ImageResponse(generations);
        }
        // Workers AI envuelve el resultado en "result"; la imagen llega en
        // "image", ya en base64, lista para un data URI.
        if (response.get("result") instanceof Map<?, ?> result
                && result.get("image") instanceof String b64
                && !b64.isBlank()) {
            generations.add(new ImageGeneration(new Image(null, b64)));
        }
        if (generations.isEmpty()) {
            log.warn("[IMAGE] Workers AI no devolvio ninguna imagen utilizable: {}",
                    response.get("errors"));
        }
        return new ImageResponse(generations);
    }
}
