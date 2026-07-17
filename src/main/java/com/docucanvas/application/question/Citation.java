package com.docucanvas.application.question;

/**
 * Cita/fuente recuperada por el retrieval. {@code score} es la similitud coseno
 * real (0..1), o {@code null} si el almacén no la proporciona.
 */
public record Citation(String source, String content, Double score) {}
