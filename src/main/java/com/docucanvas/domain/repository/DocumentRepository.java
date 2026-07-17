package com.docucanvas.domain.repository;

import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    Document save(Document document);

    Optional<Document> findById(UUID id);

    List<Document> findAll(int limit, int offset);

    /**
     * Listado de documentos sin el binario del archivo, paginado.
     * Optimizado para el endpoint de listado (no serializa {@code fileContent}).
     */
    List<DocumentSummary> findSummaries(int limit, int offset);

    void delete(UUID id);
}
