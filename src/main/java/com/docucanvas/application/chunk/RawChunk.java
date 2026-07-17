package com.docucanvas.application.chunk;

/**
 * Chunk vectorizado tal como lo entrega la capa de persistencia, antes de
 * proyección PCA y clustering. Modelo interno de la capa de aplicación
 * (no expuesto por la API).
 */
public record RawChunk(
        String id,
        String content,
        double[] embedding,
        String documentName
) {}
