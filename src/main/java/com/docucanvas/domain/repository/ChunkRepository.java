package com.docucanvas.domain.repository;

import com.docucanvas.domain.model.DocumentChunk;

import java.util.List;

public interface ChunkRepository {

    /**
     * Performs a vector similarity search to find the top-k most relevant chunks.
     *
     * @param embeddingVector the query embedding
     * @param topK           number of chunks to retrieve
     * @return list of matching chunks ordered by similarity
     */
    List<DocumentChunk> findSimilar(float[] embeddingVector, int topK);
}
