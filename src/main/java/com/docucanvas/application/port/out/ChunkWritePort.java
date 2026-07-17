package com.docucanvas.application.port.out;

import java.util.UUID;

/**
 * Puerto de salida para la escritura/eliminación de chunks vectorizados.
 *
 * <p>El almacén de vectores (PGVectorStore) es el único dueño de la tabla
 * {@code document_chunks}; el borrado se hace por el {@code documentId}
 * almacenado en la metadata del chunk.
 */
public interface ChunkWritePort {

    /** Elimina todos los chunks (y sus vectores) asociados a un documento. */
    void deleteByDocument(UUID documentId);
}
