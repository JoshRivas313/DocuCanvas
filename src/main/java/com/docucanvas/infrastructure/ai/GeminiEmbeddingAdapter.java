package com.docucanvas.infrastructure.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeminiEmbeddingAdapter {

    private final GeminiRestClient geminiRestClient;

    public GeminiEmbeddingAdapter(GeminiRestClient geminiRestClient) {
        this.geminiRestClient = geminiRestClient;
    }

    /**
     * Genera un embedding para un único texto (usado en la búsqueda RAG).
     */
    public float[] embed(String text) {
        return geminiRestClient.embed(text);
    }

    /**
     * Genera embeddings en lote para fragmentos de texto (usado en la ingesta).
     */
    public List<float[]> embedChunks(List<String> chunks) {
        return chunks.stream()
                .map(geminiRestClient::embed)
                .toList();
    }
}
