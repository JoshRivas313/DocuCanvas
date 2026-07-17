package com.docucanvas.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Agregado de dominio que representa un documento y su ciclo de vida de ingesta.
 *
 * <p>Las transiciones de estado ({@link #markProcessing()}, {@link #markReady(int)},
 * {@link #markFailed()}) están encapsuladas para proteger la invariante del flujo
 * {@code PENDING → PROCESSING → READY/FAILED}. No expone setters de estado sueltos
 * que permitan dejar el agregado en un estado inconsistente.
 */
public class Document {
    private UUID id;
    private String title;
    private String sourceType;
    private DocumentStatus status;
    private Integer chunkCount;
    private List<String> tags;
    private byte[] fileContent;
    private Instant createdAt;
    private Instant updatedAt;

    public Document() {}

    public Document(UUID id, String title, String sourceType, DocumentStatus status, Integer chunkCount, List<String> tags, byte[] fileContent, Instant createdAt, Instant updatedAt) {
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

    public static DocumentBuilder builder() {
        return new DocumentBuilder();
    }

    // ── Comportamiento de dominio (transiciones de estado) ────────────────────

    /** Marca el inicio del procesamiento. Solo válido desde PENDING. */
    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
        touch();
    }

    /** Marca la ingesta como completada con éxito, fijando el número de chunks. */
    public void markReady(int chunkCount) {
        this.status = DocumentStatus.READY;
        this.chunkCount = chunkCount;
        touch();
    }

    /** Marca la ingesta como fallida. */
    public void markFailed() {
        this.status = DocumentStatus.FAILED;
        touch();
    }

    /** Adjunta (o reemplaza) el binario original del archivo. */
    public void attachContent(byte[] content) {
        this.fileContent = content;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ── Accesores de solo lectura ─────────────────────────────────────────────

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getSourceType() { return sourceType; }
    public DocumentStatus getStatus() { return status; }
    public Integer getChunkCount() { return chunkCount; }
    public List<String> getTags() { return tags; }
    public byte[] getFileContent() { return fileContent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static class DocumentBuilder {
        private UUID id;
        private String title;
        private String sourceType;
        private DocumentStatus status;
        private Integer chunkCount;
        private List<String> tags;
        private byte[] fileContent;
        private Instant createdAt;
        private Instant updatedAt;

        public DocumentBuilder id(UUID id) { this.id = id; return this; }
        public DocumentBuilder title(String title) { this.title = title; return this; }
        public DocumentBuilder sourceType(String sourceType) { this.sourceType = sourceType; return this; }
        public DocumentBuilder status(DocumentStatus status) { this.status = status; return this; }
        public DocumentBuilder chunkCount(Integer chunkCount) { this.chunkCount = chunkCount; return this; }
        public DocumentBuilder tags(List<String> tags) { this.tags = tags; return this; }
        public DocumentBuilder fileContent(byte[] fileContent) { this.fileContent = fileContent; return this; }
        public DocumentBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public DocumentBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Document build() {
            return new Document(id, title, sourceType, status, chunkCount, tags, fileContent, createdAt, updatedAt);
        }
    }
}
