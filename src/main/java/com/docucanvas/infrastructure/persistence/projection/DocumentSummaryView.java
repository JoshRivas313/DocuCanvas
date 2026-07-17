package com.docucanvas.infrastructure.persistence.projection;

import com.docucanvas.domain.model.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Proyección cerrada de Spring Data para el listado de documentos.
 *
 * <p>Al declarar únicamente estos getters, Spring Data genera un {@code SELECT}
 * que trae solo estas columnas y <b>excluye {@code file_content} (BYTEA)</b>,
 * evitando cargar el binario del archivo en el listado.
 */
public interface DocumentSummaryView {
    UUID getId();
    String getTitle();
    String getSourceType();
    DocumentStatus getStatus();
    Integer getChunkCount();
    Instant getCreatedAt();
    Instant getUpdatedAt();
}
