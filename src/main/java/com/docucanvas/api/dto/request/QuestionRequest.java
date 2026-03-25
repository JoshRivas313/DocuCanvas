package com.docucanvas.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record QuestionRequest(
    @NotBlank(message = "La pregunta no puede estar vacía")
    String question,
    Integer maxChunks
) {
    public QuestionRequest {
        if (maxChunks == null) maxChunks = 5;
    }
}
