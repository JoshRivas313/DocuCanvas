package com.docucanvas.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vista de solo lectura de un {@link Document} sin el binario ({@code fileContent}).
 *
 * <p>Se usa para el listado {@code GET /api/v1/documents}, evitando serializar
 * el contenido físico del archivo (potencialmente varios MB en Base64) en cada
 * respuesta. El binario se sirve únicamente bajo demanda vía
 * {@code GET /api/v1/documents/{id}/file}.
 */
public record DocumentSummary(
        UUID id,
        String title,
        String sourceType,
        DocumentStatus status,
        Integer chunkCount,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {}
