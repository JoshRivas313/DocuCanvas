package com.docucanvas.application.rag;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Resultado de la fase de recuperación del pipeline RAG.
 *
 * <p>Además de los documentos, transporta <em>cómo</em> se obtuvieron: el topK
 * realmente aplicado (que puede ser menor que el pedido, si la petición excedía
 * el tope de configuración) y si hubo que relajar el umbral de similitud. Ese
 * segundo dato no es telemetría interna: alimenta el panel de transparencia de
 * la respuesta, donde al usuario se le dice con qué calidad de evidencia se
 * construyó lo que está leyendo.
 *
 * @param documents            chunks recuperados, ordenados por similitud
 * @param effectiveTopK        topK realmente usado tras aplicar el tope configurado
 * @param usedRelaxedThreshold {@code true} si la búsqueda estricta no devolvió nada
 *                             y hubo que reintentar con el umbral relajado
 */
public record RetrievalResult(
        List<Document> documents,
        int effectiveTopK,
        boolean usedRelaxedThreshold) {

    public static RetrievalResult empty(int effectiveTopK) {
        return new RetrievalResult(List.of(), effectiveTopK, false);
    }

    public boolean isEmpty() {
        return documents.isEmpty();
    }
}
