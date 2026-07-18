package com.docucanvas.application.usecase;

import java.util.Set;

/** Política de tipos de archivo admitidos para ingesta, compartida por todos los use cases. */
final class AllowedFileTypes {

    private static final Set<String> ALLOWED = Set.of("PDF", "DOCX", "DOC", "TXT", "TEXT", "MD");

    private AllowedFileTypes() {}

    static boolean isAllowed(String sourceType) {
        return ALLOWED.contains(sourceType);
    }
}
