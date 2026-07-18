package com.docucanvas.application.question;

import java.util.List;

/**
 * Metadatos de transparencia sobre cómo se construyó una respuesta RAG:
 * confianza, conceptos, relaciones, alcance de la búsqueda y explicación del
 * proceso — todo derivado de datos reales de la propia consulta, nunca
 * inventado.
 *
 * @param confidenceLevel     nivel cualitativo derivado de {@code confidenceScore}
 * @param confidenceScore     promedio de similitud coseno de los chunks recuperados (0..1)
 * @param keyConcepts         conceptos clave extraídos del contexto recuperado
 * @param conceptRelations    relación de cada concepto clave con sus subtemas, generada por el LLM
 * @param documentsUsed       nombres de los documentos realmente citados en la respuesta
 * @param totalChunksAnalyzed total de chunks en el alcance de la búsqueda (documento filtrado o todo el sistema)
 * @param howItWasFound       explicación en lenguaje natural del proceso de recuperación, construida server-side
 * @param partialMatch        {@code true} si la evidencia es parcialmente relevante, no una respuesta directa
 */
public record RetrievalInsights(
        ConfidenceLevel confidenceLevel,
        double confidenceScore,
        List<String> keyConcepts,
        List<ConceptRelation> conceptRelations,
        List<String> documentsUsed,
        int totalChunksAnalyzed,
        String howItWasFound,
        boolean partialMatch
) {}
