package com.docucanvas.api.dto.response;

import java.util.List;

/**
 * Respuesta del pipeline RAG.
 *
 * @param imageUrl     representación visual del contenido recuperado
 * @param imageSource  mecanismo que produjo la imagen: {@code GENERATIVE_IMAGE_MODEL}
 *                     si la generó un modelo de IA, {@code LOCAL_SVG_FALLBACK} si es
 *                     el diagrama local, {@code NONE} si no hubo imagen
 * @param visualPrompt prompt que el LLM derivó del contexto y que originó la
 *                     imagen; {@code null} si el modelo no produjo salida
 *                     estructurada. Exponerlo hace auditable la relación entre
 *                     el documento y lo que se dibujó
 */
public record QuestionResponse(
    String question,
    String answer,
    List<CitationDTO> citations,
    int chunkCount,
    String imageUrl,
    String imageSource,
    String visualPrompt,
    long retrievalTimeMs,
    long generationTimeMs,
    long imageTimeMs,
    RetrievalInsightsDTO insights
) {}
