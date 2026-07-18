package com.docucanvas.application.question;

/**
 * Cita/fuente recuperada por el retrieval. {@code score} es la similitud coseno
 * real (0..1), o {@code null} si el almacén no la proporciona. {@code page} es
 * la página de origen dentro del documento (1-indexada), o {@code null} si el
 * formato no tiene noción de página (p.ej. TXT) o el chunk se ingirió antes de
 * que existiera el tracking de página.
 */
public record Citation(String source, String content, Double score, Integer page) {}
