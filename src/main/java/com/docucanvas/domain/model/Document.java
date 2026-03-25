package com.docucanvas.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Document {
    private UUID id;
    private String title;
    private String sourceType;
    private DocumentStatus status;
    private Integer chunkCount;
    private List<String> tags;
    private Instant createdAt;
    private Instant updatedAt;

    public Document() {}

    public Document(UUID id, String title, String sourceType, DocumentStatus status, Integer chunkCount, List<String> tags, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.sourceType = sourceType;
        this.status = status;
        this.chunkCount = chunkCount;
        this.tags = tags;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DocumentBuilder builder() {
        return new DocumentBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static class DocumentBuilder {
        private UUID id;
        private String title;
        private String sourceType;
        private DocumentStatus status;
        private Integer chunkCount;
        private List<String> tags;
        private Instant createdAt;
        private Instant updatedAt;

        public DocumentBuilder id(UUID id) { this.id = id; return this; }
        public DocumentBuilder title(String title) { this.title = title; return this; }
        public DocumentBuilder sourceType(String sourceType) { this.sourceType = sourceType; return this; }
        public DocumentBuilder status(DocumentStatus status) { this.status = status; return this; }
        public DocumentBuilder chunkCount(Integer chunkCount) { this.chunkCount = chunkCount; return this; }
        public DocumentBuilder tags(List<String> tags) { this.tags = tags; return this; }
        public DocumentBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public DocumentBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Document build() {
            return new Document(id, title, sourceType, status, chunkCount, tags, createdAt, updatedAt);
        }
    }
}

