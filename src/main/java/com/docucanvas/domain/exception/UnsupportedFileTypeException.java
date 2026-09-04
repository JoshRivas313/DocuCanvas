package com.docucanvas.domain.exception;

/** Se lanza cuando se intenta ingerir un archivo de un tipo no permitido. */
public class UnsupportedFileTypeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsupportedFileTypeException(String sourceType) {
        super("Tipo de archivo no soportado: " + sourceType);
    }

    private UnsupportedFileTypeException(String message, boolean unused) {
        super(message);
    }

    /**
     * El archivo declara una extensión permitida, pero su contenido real es de
     * otro tipo. El mensaje nombra ambos para que el usuario legítimo pueda
     * corregirlo (p.ej. un {@code .pdf} que en realidad es un {@code .docx}
     * renombrado), sin revelar detalles internos del detector.
     */
    public static UnsupportedFileTypeException contentMismatch(String declaredType, String detectedMediaType) {
        return new UnsupportedFileTypeException(
                "El contenido del archivo no corresponde con su extensión ." + declaredType.toLowerCase()
                        + " (contenido detectado: " + detectedMediaType + ")", true);
    }
}
