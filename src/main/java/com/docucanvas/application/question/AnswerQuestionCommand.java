package com.docucanvas.application.question;

import java.util.UUID;

/**
 * Comando de aplicación para el pipeline RAG. Modelo propio de la capa de
 * aplicación: desacopla el caso de uso del DTO HTTP de entrada.
 */
public record AnswerQuestionCommand(
        String question,
        Integer maxChunks,
        UUID documentId
) {}
