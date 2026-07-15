package com.docucanvas.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class JpaDocumentChunkEntity {
    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "embedding", columnDefinition = "vector(768)")
    private float[] embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private String metadata; // Should be JSON but string is safer for simple mapping

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public JpaDocumentChunkEntity() {
    }

    public JpaDocumentChunkEntity(UUID id, UUID documentId, String content, float[] embedding, String metadata,
            int chunkIndex, Instant createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.content = content;
        this.embedding = embedding;
        this.metadata = metadata;
        this.chunkIndex = chunkIndex;
        this.createdAt = createdAt;
    }

    public static JpaDocumentChunkEntityBuilder builder() {
        return new JpaDocumentChunkEntityBuilder();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public static class JpaDocumentChunkEntityBuilder {
        private UUID id;
        private UUID documentId;
        private String content;
        private float[] embedding;
        private String metadata;
        private int chunkIndex;
        private Instant createdAt;

        public JpaDocumentChunkEntityBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public JpaDocumentChunkEntityBuilder documentId(UUID documentId) {
            this.documentId = documentId;
            return this;
        }

        public JpaDocumentChunkEntityBuilder content(String content) {
            this.content = content;
            return this;
        }

        public JpaDocumentChunkEntityBuilder embedding(float[] embedding) {
            this.embedding = embedding;
            return this;
        }

        public JpaDocumentChunkEntityBuilder metadata(String metadata) {
            this.metadata = metadata;
            return this;
        }

        public JpaDocumentChunkEntityBuilder chunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
            return this;
        }

        public JpaDocumentChunkEntityBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public JpaDocumentChunkEntity build() {
            return new JpaDocumentChunkEntity(id, documentId, content, embedding, metadata, chunkIndex, createdAt);
        }
    }
}
