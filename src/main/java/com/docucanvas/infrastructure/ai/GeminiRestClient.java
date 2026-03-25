package com.docucanvas.infrastructure.ai;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class GeminiRestClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GeminiRestClient.class);

    @Value("${spring.ai.google.ai.gemini.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    public GeminiRestClient() {}

    public String generate(String prompt) {
        String url = String.format("%s/gemini-1.5-flash:generateContent?key=%s", BASE_URL, apiKey);

        Map<String, Object> request = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))
            )
        );

        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            if (response != null && response.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                Map<String, Object> firstCandidate = candidates.get(0);
                Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                return (String) parts.get(0).get("text");
            }
        } catch (Exception e) {
            log.error("Error llamando a Gemini Generate API", e);
            return "Error al generar respuesta: " + e.getMessage();
        }
        return "Sin respuesta del modelo.";
    }

    public float[] embed(String text) {
        String url = String.format("%s/text-embedding-004:embedContent?key=%s", BASE_URL, apiKey);

        Map<String, Object> request = Map.of(
            "model", "models/text-embedding-004",
            "content", Map.of("parts", List.of(Map.of("text", text)))
        );

        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            if (response != null && response.containsKey("embedding")) {
                Map<String, Object> embedding = (Map<String, Object>) response.get("embedding");
                List<Double> values = (List<Double>) embedding.get("values");
                float[] result = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    result[i] = values.get(i).floatValue();
                }
                return result;
            }
        } catch (Exception e) {
            log.error("Error llamando a Gemini Embedding API", e);
        }
        return new float[768]; // Fallback neutral
    }
}
