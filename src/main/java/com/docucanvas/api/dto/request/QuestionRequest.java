package com.docucanvas.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Petición de consulta al pipeline RAG de DocuCanvas.
 *
 * <p><b>Por qué hay cotas superiores:</b> en la versión base, {@code maxChunks}
 * llegaba sin límite alguno hasta {@code SearchRequest.topK()}. Una petición con
 * {@code maxChunks: 5000} construía un prompt que desbordaba la ventana de
 * contexto configurada ({@code num-ctx: 4096}) y, con un proveedor de pago como
 * Gemini, convertía un endpoint público en un amplificador de coste por tokens.
 * El rate limiting acota la frecuencia de las peticiones, no el tamaño de cada
 * una: son dos controles distintos y hacen falta los dos.
 *
 * <p>El tope real aplicado es el mínimo entre este {@code @Max} y
 * {@code docucanvas.rag.retrieval.max-top-k}; la anotación rechaza la petición
 * en el borde de la API con un 400 explicativo, en vez de recortar en silencio.
 *
 * @param question   pregunta del usuario (obligatoria, máx. 2000 caracteres)
 * @param maxChunks  número de fragmentos a recuperar (1..20, por defecto 5)
 * @param documentId UUID del documento al que limitar la búsqueda semántica (opcional)
 */
public record QuestionRequest(
    @NotBlank(message = "La pregunta no puede estar vacía")
    @Size(max = 2000, message = "La pregunta no puede superar los 2000 caracteres")
    String question,

    @Positive(message = "maxChunks debe ser mayor que 0")
    @Max(value = 20, message = "maxChunks no puede superar 20: por encima de ese valor el "
            + "contexto desborda la ventana del modelo sin mejorar la respuesta")
    Integer maxChunks,

    UUID documentId
) {
    public QuestionRequest {
        if (maxChunks == null) maxChunks = 5;
    }
}
