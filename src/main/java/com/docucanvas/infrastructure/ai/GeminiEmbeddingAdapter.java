package com.docucanvas.infrastructure.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GeminiEmbeddingAdapter {

    private final EmbeddingModel embeddingModel;

    public List<float[]> embedChunks(List<String> chunks) {
        return embeddingModel.embed(chunks);
    }
}
