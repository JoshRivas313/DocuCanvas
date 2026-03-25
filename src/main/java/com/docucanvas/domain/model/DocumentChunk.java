package com.docucanvas.domain.model;

import java.time.Instant;
import java.util.UUID;

public record DocumentChunk(
    UUID id,
    UUID documentId,
    String content,
    float[] embedding,
    int chunkIndex,
    Instant createdAt
) {}
