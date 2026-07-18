package com.docucanvas.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del agregado {@link Document}: las transiciones de estado encapsuladas
 * deben mantener la invariante PENDING → PROCESSING → READY/FAILED y refrescar
 * {@code updatedAt} en cada mutación.
 */
@DisplayName("Document — Agregado de dominio")
class DocumentTest {

    private Document nuevoDocumento() {
        Instant origen = Instant.parse("2026-01-01T00:00:00Z");
        return Document.builder()
                .id(UUID.randomUUID())
                .title("doc.pdf")
                .sourceType("PDF")
                .status(DocumentStatus.PENDING)
                .chunkCount(0)
                .tags(List.of())
                .createdAt(origen)
                .updatedAt(origen)
                .build();
    }

    @Test
    @DisplayName("markProcessing debe pasar a PROCESSING y refrescar updatedAt")
    void markProcessingTransicionaYRefresca() {
        Document doc = nuevoDocumento();
        Instant antes = doc.getUpdatedAt();

        doc.markProcessing();

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(doc.getUpdatedAt()).isAfter(antes);
    }

    @Test
    @DisplayName("markReady debe pasar a READY y fijar el número de chunks")
    void markReadyFijaEstadoYChunks() {
        Document doc = nuevoDocumento();
        doc.markProcessing();

        doc.markReady(7);

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(doc.getChunkCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("markFailed debe pasar a FAILED sin tocar el chunkCount")
    void markFailedNoTocaChunkCount() {
        Document doc = nuevoDocumento();
        doc.markProcessing();

        doc.markFailed();

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.getChunkCount()).isZero();
    }
}
