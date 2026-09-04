package com.docucanvas.application.usecase;

import java.util.Map;
import java.util.Set;

/**
 * Política de tipos de archivo admitidos para ingesta, compartida por todos los
 * use cases.
 *
 * <p><b>Por qué no basta la extensión:</b> la versión base validaba únicamente
 * el sufijo del nombre del archivo. Renombrar {@code payload.exe} a
 * {@code payload.pdf} pasaba la validación y entregaba los bytes íntegros a
 * Tika/PDFBox — parsers de formatos complejos que son, históricamente, una
 * superficie de ataque real (denegación de servicio por documentos malformados
 * y, en versiones antiguas, ejecución de código). El nombre del archivo es un
 * dato que controla el cliente; los magic bytes, no.
 *
 * <p>La política es por tanto de dos pasos: la extensión debe estar permitida
 * <em>y</em> el tipo detectado por contenido debe ser coherente con ella.
 */
final class AllowedFileTypes {

    /** Formatos textuales sin firma binaria propia: se validan por familia {@code text/*}. */
    private static final Set<String> TEXT_LIKE = Set.of("TXT", "TEXT", "MD");

    /**
     * Media types aceptables por extensión. Se incluyen las variantes genéricas
     * que Tika devuelve para contenedores OOXML/OLE cuando no puede afinar más,
     * para no rechazar documentos legítimos.
     */
    private static final Map<String, Set<String>> EXPECTED_MEDIA_TYPES = Map.of(
            "PDF", Set.of("application/pdf"),
            "DOCX", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/x-tika-ooxml",
                    "application/zip"),
            "DOC", Set.of(
                    "application/msword",
                    "application/x-tika-msoffice"),
            "TXT", Set.of("text/plain"),
            "TEXT", Set.of("text/plain"),
            "MD", Set.of("text/plain", "text/markdown", "text/x-web-markdown"));

    private AllowedFileTypes() {}

    static boolean isAllowed(String sourceType) {
        return sourceType != null && EXPECTED_MEDIA_TYPES.containsKey(sourceType);
    }

    /**
     * Comprueba que el tipo detectado por contenido sea coherente con la
     * extensión declarada.
     *
     * <p>Para formatos textuales se acepta cualquier {@code text/*}: un Markdown,
     * un CSV y un texto plano son bytes indistinguibles y todos son seguros de
     * parsear. Para PDF/DOC/DOCX la comprobación es estricta, porque son
     * precisamente los que activan parsers complejos.
     *
     * @param sourceType extensión declarada, ya normalizada a mayúsculas
     * @param detectedMediaType media type detectado a partir de los bytes
     */
    static boolean matchesDetectedType(String sourceType, String detectedMediaType) {
        if (!isAllowed(sourceType) || detectedMediaType == null) {
            return false;
        }
        String detected = detectedMediaType.toLowerCase().split(";")[0].trim();

        if (TEXT_LIKE.contains(sourceType)) {
            return detected.startsWith("text/");
        }
        return EXPECTED_MEDIA_TYPES.get(sourceType).contains(detected);
    }
}
