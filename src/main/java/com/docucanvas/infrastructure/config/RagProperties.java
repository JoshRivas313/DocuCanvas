package com.docucanvas.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros del pipeline RAG, externalizados a configuración.
 *
 * <p><b>Por qué existe:</b> en la versión base estos valores vivían como
 * constantes Java repartidas entre {@code IngestionService} (los cinco
 * argumentos del {@code TokenTextSplitter}) y {@code QuestionService} (topK por
 * defecto, umbrales de similitud, tope de tokens de salida). Tunear el
 * chunking o el retrieval — la actividad más frecuente al ajustar un sistema
 * RAG a un corpus nuevo — exigía recompilar. Aquí quedan en un único sitio,
 * versionable y sobreescribible por perfil o variable de entorno.
 *
 * <p>Todos los valores llevan un {@link DefaultValue} idéntico a la constante
 * que sustituyen, de modo que el comportamiento por defecto es exactamente el
 * de la versión base: esto es una extracción, no un cambio de tuning.
 */
@ConfigurationProperties(prefix = "docucanvas.rag")
public record RagProperties(
        @DefaultValue Chunking chunking,
        @DefaultValue Retrieval retrieval,
        @DefaultValue Generation generation,
        @DefaultValue Visual visual) {

    /**
     * Parámetros de troceado del documento.
     *
     * @param chunkSize            tokens objetivo por chunk. Más pequeño = recuperación más
     *                             precisa pero más riesgo de partir una idea en dos;
     *                             más grande = más contexto por chunk pero la similitud
     *                             se diluye entre varios temas.
     * @param minChunkSizeChars    caracteres mínimos antes de considerar cerrar un chunk
     *                             en un límite de frase.
     * @param minChunkLengthToEmbed longitud mínima para que un fragmento merezca un
     *                             embedding (descarta restos como cabeceras sueltas).
     * @param maxNumChunks         tope de chunks por documento, cortafuegos ante un PDF
     *                             gigantesco.
     * @param keepSeparator        conservar los saltos de línea dentro del chunk.
     */
    public record Chunking(
            @DefaultValue("200") int chunkSize,
            @DefaultValue("50") int minChunkSizeChars,
            @DefaultValue("5") int minChunkLengthToEmbed,
            @DefaultValue("10000") int maxNumChunks,
            @DefaultValue("true") boolean keepSeparator) {}

    /**
     * Parámetros de recuperación semántica.
     *
     * <p><b>Estrategia de dos umbrales:</b> la versión base hacía una búsqueda con
     * umbral 0.3 y, si volvía vacía, reintentaba con 0.0 — con ambos números
     * incrustados en el código sin nombre. Aquí la estrategia es explícita:
     * {@code strictThreshold} es el corte normal y {@code relaxedThreshold} el de
     * segunda oportunidad para preguntas abstractas o de síntesis, cuya
     * similitud coseno es estructuralmente más baja que la de una pregunta
     * factual aunque el contexto sí sea útil.
     *
     * @param defaultTopK     chunks a recuperar si la petición no especifica otro valor
     * @param maxTopK         tope duro de chunks recuperables por petición; protege la
     *                        ventana de contexto del modelo y, con proveedores de pago,
     *                        la factura por tokens
     * @param strictThreshold similitud mínima en la búsqueda principal
     * @param relaxedThreshold similitud mínima en el reintento cuando la principal no
     *                        devuelve nada
     */
    public record Retrieval(
            @DefaultValue("5") int defaultTopK,
            @DefaultValue("20") int maxTopK,
            @DefaultValue("0.3") double strictThreshold,
            @DefaultValue("0.0") double relaxedThreshold) {}

    /**
     * Parámetros de generación de texto.
     *
     * <p>Las constantes de timeout provienen de la auditoría de rendimiento de la
     * versión base (medición directa sobre CPU sin GPU): el timeout no es fijo,
     * se calcula sobre el tamaño real del prompt construido. Externalizarlas
     * permite recalibrarlas al cambiar de hardware o de proveedor — al migrar a
     * Gemini, por ejemplo, los throughputs medidos para llama3.2 en CPU dejan de
     * aplicar por completo.
     *
     * @param model                  identificador del modelo de chat. Vive aquí, y no
     *                               en {@code spring.ai.ollama.*}, para que el servicio
     *                               no lea propiedades de un proveedor concreto: al
     *                               activar el perfil {@code gemini} basta con
     *                               sobreescribir este valor
     * @param temperature            temperatura de muestreo
     * @param maxOutputTokens        tope de tokens de la respuesta
     * @param charsPerToken          ratio caracteres/token calibrado para español
     * @param prefillTokensPerSecond throughput de procesado del prompt de entrada
     * @param outputTokensPerSecond  throughput de generación de la respuesta
     * @param timeoutMarginSeconds   margen añadido al timeout calculado
     * @param minTimeoutSeconds      suelo del timeout dinámico
     * @param maxTimeoutSeconds      techo del timeout dinámico
     */
    public record Generation(
            @DefaultValue("llama3.2") String model,
            @DefaultValue("0.7") double temperature,
            @DefaultValue("400") int maxOutputTokens,
            @DefaultValue("3.7") double charsPerToken,
            @DefaultValue("25.0") double prefillTokensPerSecond,
            @DefaultValue("5.0") double outputTokensPerSecond,
            @DefaultValue("15") long timeoutMarginSeconds,
            @DefaultValue("30") long minTimeoutSeconds,
            @DefaultValue("150") long maxTimeoutSeconds) {}

    /**
     * Parámetros del render visual.
     *
     * <p>Solo aplican cuando hay un proveedor de generación de imágenes
     * configurado; el respaldo SVG local tiene sus propias dimensiones fijas
     * porque las calcula el propio generador.
     *
     * @param width  ancho solicitado al modelo de imagen
     * @param height alto solicitado al modelo de imagen
     */
    public record Visual(
            @DefaultValue("1024") int width,
            @DefaultValue("1024") int height) {}
}
