package com.docucanvas.domain.exception;

/** Se lanza cuando falla la extracción de texto de un archivo (p. ej. Tika). */
public class TextExtractionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TextExtractionException(String filename, Throwable cause) {
        super("No se pudo extraer texto del archivo: " + filename, cause);
    }
}
