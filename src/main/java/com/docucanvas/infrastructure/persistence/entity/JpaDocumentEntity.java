package com.docucanvas.infrastructure.persistence.entity;

import com.docucanvas.domain.model.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class JpaDocumentEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags")
    private List<String> tags;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "file_content")
    private byte[] fileContent;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public JpaDocumentEntity() {}

    public JpaDocumentEntity(UUID id, String title, String sourceType, DocumentStatus status, Integer chunkCount, List<String> tags, byte[] fileContent, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.sourceType = sourceType;
        this.status = status;
        this.chunkCount = chunkCount;
        this.tags = tags;
        this.fileContent = fileContent;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static JpaDocumentEntityBuilder builder() {
        return new JpaDocumentEntityBuilder();
    }

    // Solo getters: Hibernate usa acceso por campo (el @Id está en el campo, no
    // en un getter) y ningún código de la aplicación mutaba esta entidad tras
    // construirla — toda escritura pasa por el builder o el constructor.
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getSourceType() { return sourceType; }
    public DocumentStatus getStatus() { return status; }
    public Integer getChunkCount() { return chunkCount; }
    public List<String> getTags() { return tags; }
    public byte[] getFileContent() { return fileContent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (chunkCount == null) chunkCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public static class JpaDocumentEntityBuilder {
        private UUID id;
        private String title;
        private String sourceType;
        private DocumentStatus status;
        private Integer chunkCount;
        private List<String> tags;
        private byte[] fileContent;
        private Instant createdAt;
        private Instant updatedAt;

        public JpaDocumentEntityBuilder id(UUID id) { this.id = id; return this; }
        public JpaDocumentEntityBuilder title(String title) { this.title = title; return this; }
        public JpaDocumentEntityBuilder sourceType(String sourceType) { this.sourceType = sourceType; return this; }
        public JpaDocumentEntityBuilder status(DocumentStatus status) { this.status = status; return this; }
        public JpaDocumentEntityBuilder chunkCount(Integer chunkCount) { this.chunkCount = chunkCount; return this; }
        public JpaDocumentEntityBuilder tags(List<String> tags) { this.tags = tags; return this; }
        public JpaDocumentEntityBuilder fileContent(byte[] fileContent) { this.fileContent = fileContent; return this; }
        public JpaDocumentEntityBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public JpaDocumentEntityBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public JpaDocumentEntity build() {
            return new JpaDocumentEntity(id, title, sourceType, status, chunkCount, tags, fileContent, createdAt, updatedAt);
        }
    }
}

