package com.docucanvas.api.dto.response;

/**
 * Representa una cita o fuente bibliográfica recuperada de un documento.
 */
public record CitationDTO(
    String source,
    String content,
    Double score
) {}
