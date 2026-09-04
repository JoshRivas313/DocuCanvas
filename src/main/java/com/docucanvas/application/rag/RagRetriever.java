package com.docucanvas.application.rag;

import com.docucanvas.infrastructure.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fase de recuperación del pipeline RAG: pregunta → embedding → búsqueda por
 * similitud → top-K chunks.
 *
 * <p><b>Por qué es una clase propia y no un {@code QuestionAnswerAdvisor} de
 * Spring AI:</b> el Advisor recupera el contexto y lo inyecta en el prompt en
 * un solo paso opaco, que es exactamente lo que se quiere en el caso común. Aquí
 * no sirve por dos razones concretas:
 *
 * <ol>
 *   <li>La <b>estrategia de dos umbrales</b> — buscar con {@code strictThreshold} y,
 *       solo si vuelve vacío, reintentar con {@code relaxedThreshold} — no es
 *       expresable dentro del Advisor, que ejecuta una única búsqueda. Sin ella,
 *       las preguntas abstractas o de síntesis (cuya similitud coseno es
 *       estructuralmente más baja aunque el contexto sí sirva) se quedan sin
 *       ninguna evidencia.</li>
 *   <li>La UI necesita los documentos recuperados <b>con su score</b> para las
 *       citaciones y el panel de confianza. Cuando de todas formas hace falta
 *       tener los documentos en la mano, el Advisor no ahorra trabajo: solo
 *       esconde dónde ocurre.</li>
 * </ol>
 *
 * <p>Es decir: la abstracción del framework se usa donde encaja
 * ({@link VectorStore}, {@link SearchRequest}, {@code FilterExpressionBuilder})
 * y se deja fuera donde el caso de uso pide algo que no cubre — una decisión
 * explícita, no un descuido.
 */
@Component
public class RagRetriever {

    private static final Logger log = LoggerFactory.getLogger(RagRetriever.class);

    private final VectorStore vectorStore;
    private final RagProperties.Retrieval config;

    public RagRetriever(VectorStore vectorStore, RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.config = ragProperties.retrieval();
    }

    /**
     * Recupera los chunks más similares a la pregunta.
     *
     * @param question       pregunta en lenguaje natural
     * @param requestedTopK  topK pedido por el cliente, o {@code null} para el valor por defecto
     * @param documentFilter UUID del documento al que limitar la búsqueda, o {@code null} para todo el corpus
     */
    public RetrievalResult retrieve(String question, Integer requestedTopK, String documentFilter) {
        int topK = resolveTopK(requestedTopK);

        List<Document> docs = search(question, topK, documentFilter, config.strictThreshold());
        if (!docs.isEmpty()) {
            return new RetrievalResult(docs, topK, false);
        }

        // Segunda oportunidad: preguntas abstractas ("¿de qué trata el documento?")
        // puntúan por debajo del umbral estricto aunque el contexto sea útil.
        log.info("Retrieval vacío con umbral {} — reintentando con umbral relajado {} para: {}",
                config.strictThreshold(), config.relaxedThreshold(), question);
        List<Document> relaxed = search(question, topK, documentFilter, config.relaxedThreshold());
        log.info("Retrieval relajado devolvió {} chunks", relaxed.size());

        return new RetrievalResult(relaxed, topK, !relaxed.isEmpty());
    }

    /**
     * Aplica el tope duro de configuración. La validación del DTO ya rechaza
     * valores fuera de rango en el borde HTTP; este recorte protege al servicio
     * cuando se le invoca desde otro punto (un job, un test, un futuro endpoint).
     */
    private int resolveTopK(Integer requestedTopK) {
        if (requestedTopK == null || requestedTopK <= 0) {
            return config.defaultTopK();
        }
        if (requestedTopK > config.maxTopK()) {
            log.warn("topK solicitado ({}) excede el máximo configurado ({}); se recorta",
                    requestedTopK, config.maxTopK());
            return config.maxTopK();
        }
        return requestedTopK;
    }

    private List<Document> search(String question, int topK, String documentFilter, double threshold) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(threshold);

        if (documentFilter != null) {
            FilterExpressionBuilder filter = new FilterExpressionBuilder();
            builder.filterExpression(filter.eq("documentId", documentFilter).build());
        }

        List<Document> results = vectorStore.similaritySearch(builder.build());
        return results != null ? results : List.of();
    }
}
