package com.docucanvas.application.port.out;

import com.docucanvas.application.chunk.RawChunk;

import java.util.List;

/**
 * Puerto de salida para la lectura de chunks vectorizados desde el almacén.
 *
 * <p>Aísla a la capa de aplicación del detalle concreto de persistencia
 * (SQL / formato de pgvector), que vive en el adaptador de infraestructura.
 */
public interface ChunkReadPort {

    /** Chunks para la vista global (limitados y ordenados de forma determinista). */
    List<RawChunk> findForGlobalView(int limit);

    /** Chunks de un documento concreto, ordenados por índice de chunk. */
    List<RawChunk> findByDocument(String documentId);
}
