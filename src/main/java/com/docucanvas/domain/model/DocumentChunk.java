package com.docucanvas.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    private UUID id;
    private UUID documentId;
    private String content;
    private float[] embedding;
    private String metadata; // JSON string
    private Instant createdAt;
}
