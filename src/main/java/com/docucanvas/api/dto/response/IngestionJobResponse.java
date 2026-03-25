package com.docucanvas.api.dto.response;

import com.docucanvas.domain.model.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

public record IngestionJobResponse(
    UUID jobId,
    UUID documentId,
    DocumentStatus status,
    Integer processedChunks,
    String error,
    Instant createdAt,
    Instant finishedAt
) {}
