package com.docucanvas.domain.repository;

import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentChunk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    Document save(Document document);
    Optional<Document> findById(UUID id);
    List<Document> findAll(int limit, int offset);
    void delete(UUID id);
    
    void saveChunks(List<DocumentChunk> chunks);
    List<DocumentChunk> findChunksByDocumentId(UUID documentId);
}
