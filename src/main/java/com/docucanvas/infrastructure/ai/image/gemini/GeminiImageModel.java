package com.docucanvas.infrastructure.ai.image.gemini;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ImageModel} sobre la Gemini Developer API (Google AI Studio).
 *
 * <p><b>Por qué esta clase existe:</b> Spring AI 1.0.0 GA no trae ningún
 * {@code ImageModel} de Google — no existe el artefacto
 * {@code spring-ai-vertex-ai-imagen} en ninguna versión, y el módulo
 * {@code spring-ai-google-genai} (1.1.0+) solo aporta chat. Además, la capa de
 * Google compatible con OpenAI, que sí sirve para el chat, <b>no expone</b>
 * {@code /images/generations}: devuelve 404, frente al 400 de autenticación que
 * devuelve el endpoint de chat. Comprobado contra la API real.
 *
 * <p>La alternativa habría sido subir a Spring AI 1.1.x (que arrastra Spring
 * Boot 3.5) o traer un segundo proveedor de pago solo para las imágenes.
 * Implementar la interfaz es más barato y no toca versiones: son unas pocas
 * decenas de líneas contra un endpoint documentado, y el resto del sistema no
 * se entera — {@code SpringAiImageModelAdapter} depende de {@link ImageModel},
 * no de quién lo implementa. Ese es exactamente el valor de la abstracción.
 *
 * <p>Usa la <b>misma clave</b> que el chat: una sola credencial de AI Studio
 * cubre texto e imagen.
 */
public class GeminiImageModel implements ImageModel {

    private static final Logger log = LoggerFactory.getLogger(GeminiImageModel.class);

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    private final GeminiImageProperties config;
    private final RestClient restClient;

    public GeminiImageModel(GeminiImageProperties config, RestClient.Builder restClientBuilder) {
        this.config = config;
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
    }

    @Override
    public ImageResponse call(ImagePrompt request) {
        String prompt = request.getInstructions().get(0).getText();
        log.info("[IMAGE] Gemini Developer API ({}) generando imagen", config.model());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/v1beta/models/{model}:generateContent", config.model())
                // La clave viaja en cabecera, no como query param: en la URL
                // acabaria en logs de acceso, trazas y cabeceras Referer.
                .header("x-goog-api-key", config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildRequestBody(prompt, request.getOptions()))
                .retrieve()
                .body(Map.class);

        return toImageResponse(response);
    }

    /**
     * Cuerpo para {@code :generateContent}.
     *
     * <p>Los modelos de imagen de la Developer API <b>no exponen</b>
     * {@code :predict}: consultando {@code /v1beta/models} con una clave real, los
     * unicos modelos con ese metodo son los de video (Veo, {@code predictLongRunning}).
     * La generacion de imagen se pide como una conversacion normal cuya respuesta
     * se fuerza a la modalidad IMAGE.
     */
    private Map<String, Object> buildRequestBody(String prompt, ImageOptions options) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseModalities", List.of("IMAGE"));

        // El modelo no acepta ancho y alto arbitrarios: trabaja con proporciones.
        // Se deriva la mas cercana a lo pedido en vez de enviar valores que
        // rechazaria.
        generationConfig.put("imageConfig", Map.of("aspectRatio", aspectRatioFrom(options)));

        return Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", generationConfig);
    }

    private String aspectRatioFrom(ImageOptions options) {
        if (options == null || options.getWidth() == null || options.getHeight() == null) {
            return "1:1";
        }
        double ratio = options.getWidth() / (double) options.getHeight();
        if (ratio > 1.5) return "16:9";
        if (ratio > 1.1) return "4:3";
        if (ratio < 0.67) return "9:16";
        if (ratio < 0.9) return "3:4";
        return "1:1";
    }

    private ImageResponse toImageResponse(Map<String, Object> response) {
        List<ImageGeneration> generations = new ArrayList<>();
        if (response == null) {
            return new ImageResponse(generations);
        }
        // La respuesta llega como partes de un candidato: la imagen viaja en
        // inlineData.data, en base64, nunca como URL.
        if (response.get("candidates") instanceof List<?> candidates) {
            for (Object candidate : candidates) {
                if (!(candidate instanceof Map<?, ?> c)) continue;
                if (!(c.get("content") instanceof Map<?, ?> content)) continue;
                if (!(content.get("parts") instanceof List<?> parts)) continue;
                for (Object part : parts) {
                    if (part instanceof Map<?, ?> pm
                            && pm.get("inlineData") instanceof Map<?, ?> inline
                            && inline.get("data") instanceof String b64
                            && !b64.isBlank()) {
                        generations.add(new ImageGeneration(new Image(null, b64)));
                    }
                }
            }
        }
        if (generations.isEmpty()) {
            log.warn("[IMAGE] La respuesta de Gemini no contenia ninguna imagen utilizable");
        }
        return new ImageResponse(generations);
    }
}
