package com.docucanvas.domain.exception;

import java.util.UUID;

/** Se lanza cuando se referencia un documento que no existe. */
public class DocumentNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DocumentNotFoundException(UUID id) {
        super("Documento no encontrado: " + id);
    }
}
