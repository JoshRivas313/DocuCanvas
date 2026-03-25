package com.docucanvas.infrastructure.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GeminiEmbeddingAdapter {

    private final EmbeddingModel embeddingModel;

    /**
     * Genera un embedding para un único texto (usado en la búsqueda RAG).
     */
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    /**
     * Genera embeddings en lote para fragmentos de texto (usado en la ingesta).
     */
    public List<float[]> embedChunks(List<String> chunks) {
        return embeddingModel.embed(chunks);
    }
}
