package com.docucanvas.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Petición de consulta al pipeline RAG de DocuCanvas.
 *
 * @param question   pregunta del usuario (obligatorio)
 * @param maxChunks  número máximo de fragmentos a recuperar (default: 5)
 * @param documentId UUID del documento al que limitar la búsqueda semántica (opcional)
 */
public record QuestionRequest(
    @NotBlank(message = "La pregunta no puede estar vacía")
    String question,
    Integer maxChunks,
    UUID documentId
) {
    public QuestionRequest {
        if (maxChunks == null) maxChunks = 5;
    }
}

