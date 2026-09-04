package com.docucanvas.application.question;

import java.util.List;

/**
 * Resultado de aplicación del pipeline RAG. El controlador lo traduce al DTO
 * HTTP de respuesta.
 *
 * <p>{@code visualPrompt} se expone deliberadamente: es la pieza que explica
 * <em>por qué</em> la imagen tiene el aspecto que tiene. Sin él, el resultado
 * visual es un truco de magia; con él, el usuario (y el público de una demo)
 * puede leer la interpretación que el modelo hizo del documento antes de ver
 * cómo la dibuja.
 *
 * @param imageSource  qué mecanismo produjo realmente la imagen
 *                     ({@code GENERATIVE_IMAGE_MODEL}, {@code LOCAL_SVG_FALLBACK}
 *                     o {@code NONE}); el sistema declara su propio camino en vez
 *                     de dejar que se asuma
 * @param visualPrompt prompt visual derivado por el LLM del contexto recuperado;
 *                     {@code null} si el modelo no produjo salida estructurada
 */
public record AnswerResult(
        String question,
        String answer,
        List<Citation> citations,
        int chunkCount,
        String imageUrl,
        String imageSource,
        String visualPrompt,
        long retrievalTimeMs,
        long generationTimeMs,
        long imageTimeMs,
        RetrievalInsights insights
) {}
