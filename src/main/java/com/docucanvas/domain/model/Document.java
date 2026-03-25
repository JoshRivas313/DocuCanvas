package com.docucanvas.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    private UUID id;
    private String title;
    private String sourceType;
    private DocumentStatus status;
    private Integer chunkCount;
    private List<String> tags;
    private Instant createdAt;
    private Instant updatedAt;
}
