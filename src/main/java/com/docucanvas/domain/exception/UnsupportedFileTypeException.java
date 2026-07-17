package com.docucanvas.domain.exception;

/** Se lanza cuando se intenta ingerir un archivo de un tipo no permitido. */
public class UnsupportedFileTypeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsupportedFileTypeException(String sourceType) {
        super("Tipo de archivo no soportado: " + sourceType);
    }
}
