package com.docucanvas.application.question;

import java.util.List;

/**
 * Resultado de aplicación del pipeline RAG. El controlador lo traduce al DTO
 * HTTP de respuesta.
 */
public record AnswerResult(
        String question,
        String answer,
        List<Citation> citations,
        int chunkCount,
        String imageUrl,
        long retrievalTimeMs,
        long generationTimeMs,
        long imageTimeMs
) {}
