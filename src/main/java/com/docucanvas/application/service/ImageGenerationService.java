package com.docucanvas.application.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio responsable de generar una representación visual del contexto
 * recuperado por el pipeline RAG.
 *
 * <p><b>Estrategia de reemplazo de DALL-E:</b> en lugar de llamar a una API
 * externa de generación de imágenes (costosa y dependiente de red), este
 * servicio genera una infografía SVG profesional de forma completamente
 * local y gratuita, usando únicamente la JVM.
 *
 * <p>El SVG producido se devuelve como Data URL {@code data:image/svg+xml;base64,...}
 * que el navegador puede renderizar directamente sin necesidad de servidor
 * externo ni almacenamiento adicional.
 */
@Service
public class ImageGenerationService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ImageGenerationService.class);

    // Palabras vacías en español e inglés que no aportan contexto visual
    private static final Set<String> STOP_WORDS = Set.of(
            "de", "la", "el", "en", "y", "a", "los", "las", "un", "una",
            "es", "se", "que", "por", "con", "no", "al", "del", "su", "lo",
            "the", "an", "in", "is", "it", "of", "to", "and", "or",
            "for", "on", "are", "at", "be", "this", "that", "with", "from",
            "was", "has", "had", "have", "not", "but", "they", "we", "you",
            "si", "como", "más", "pero", "sus", "le", "ya", "o", "este",
            "también", "hasta", "hay", "donde", "han", "quien", "siendo"
    );

    /**
     * Genera un Data URL SVG a partir de la pregunta y el contexto documental.
     * Reemplaza funcionalmente a DALL-E 3 de forma 100% local, sin costos y sin GPU.
     *
     * @param question pregunta original del usuario
     * @param context  contexto documental recuperado del VectorStore
     * @return Data URL {@code data:image/svg+xml;base64,...} listo para {@code <img src="...">}
     */
    public String generateImageDataUrl(String question, String context) {
        log.info("Generando infografía SVG local para: {}", question);

        List<String> concepts  = extractKeyConcepts(context, 6);
        List<String> sentences = extractKeySentences(context, 3);

        String svg     = buildInfographicSvg(question, concepts, sentences);
        String base64  = Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        String dataUrl = "data:image/svg+xml;base64," + base64;

        log.debug("SVG generado ({} chars, {} conceptos, {} frases)",
                svg.length(), concepts.size(), sentences.size());
        return dataUrl;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Extracción de contenido
    // ─────────────────────────────────────────────────────────────────────────

    /** Devuelve las palabras más frecuentes del contexto, descartando stop-words. */
    private List<String> extractKeyConcepts(String context, int limit) {
        if (context == null || context.isBlank()) {
            return List.of("Sin contexto disponible");
        }
        return Arrays.stream(context.split("[\\s\\p{Punct}]+"))
                .map(String::toLowerCase)
                .filter(w -> w.length() > 4)
                .filter(w -> !STOP_WORDS.contains(w))
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> capitalize(e.getKey()))
                .collect(Collectors.toList());
    }

    /** Extrae oraciones breves y representativas del contexto (30–120 caracteres). */
    private List<String> extractKeySentences(String context, int limit) {
        if (context == null || context.isBlank()) {
            return List.of();
        }
        return Arrays.stream(context.split("[.!?\\n]+"))
                .map(String::trim)
                .filter(s -> s.length() >= 30 && s.length() <= 120)
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construcción del SVG
    // ─────────────────────────────────────────────────────────────────────────

    private String buildInfographicSvg(String question,
                                        List<String> concepts,
                                        List<String> sentences) {
        int width  = 900;
        int height = 620;

        StringBuilder svg = new StringBuilder();

        // ── Cabecera SVG ──────────────────────────────────────────────────
        svg.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" " +
                "viewBox=\"0 0 %d %d\">%n", width, height, width, height));

        // ── Definiciones (gradientes y filtros) ───────────────────────────
        svg.append("<defs>\n");
        svg.append("  <linearGradient id=\"headerGrad\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"0\">\n");
        svg.append("    <stop offset=\"0%\" stop-color=\"#1e3a8a\"/>\n");
        svg.append("    <stop offset=\"100%\" stop-color=\"#2563eb\"/>\n");
        svg.append("  </linearGradient>\n");
        svg.append("  <linearGradient id=\"bgGrad\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">\n");
        svg.append("    <stop offset=\"0%\" stop-color=\"#f8faff\"/>\n");
        svg.append("    <stop offset=\"100%\" stop-color=\"#eff6ff\"/>\n");
        svg.append("  </linearGradient>\n");
        svg.append("  <filter id=\"shadow\">\n");
        svg.append("    <feDropShadow dx=\"0\" dy=\"2\" stdDeviation=\"3\" flood-opacity=\"0.12\"/>\n");
        svg.append("  </filter>\n");
        svg.append("</defs>\n");

        // ── Fondo ─────────────────────────────────────────────────────────
        svg.append(String.format(
                "<rect width=\"%d\" height=\"%d\" fill=\"url(#bgGrad)\" rx=\"12\" ry=\"12\"/>\n",
                width, height));

        // ── Header ────────────────────────────────────────────────────────
        svg.append("<rect x=\"0\" y=\"0\" width=\"900\" height=\"90\" " +
                   "fill=\"url(#headerGrad)\" rx=\"12\" ry=\"12\"/>\n");
        // cubrimos las esquinas redondeadas inferiores del header
        svg.append("<rect x=\"0\" y=\"70\" width=\"900\" height=\"20\" fill=\"url(#headerGrad)\"/>\n");

        // Icono lupa
        svg.append("<circle cx=\"42\" cy=\"45\" r=\"14\" fill=\"none\" " +
                   "stroke=\"white\" stroke-width=\"2.5\" opacity=\"0.8\"/>\n");
        svg.append("<line x1=\"52\" y1=\"55\" x2=\"62\" y2=\"65\" " +
                   "stroke=\"white\" stroke-width=\"2.5\" stroke-linecap=\"round\" opacity=\"0.8\"/>\n");

        // Etiqueta + pregunta
        svg.append("<text x=\"80\" y=\"38\" font-family=\"'Segoe UI', Arial, sans-serif\" " +
                   "font-size=\"11\" fill=\"#bfdbfe\" font-weight=\"400\" " +
                   "letter-spacing=\"1.5\">CONSULTA DOCUMENTAL</text>\n");

        String qDisplay = question.length() > 78 ? question.substring(0, 75) + "..." : question;
        svg.append(String.format(
                "<text x=\"80\" y=\"63\" font-family=\"'Segoe UI', Arial, sans-serif\" " +
                "font-size=\"15\" fill=\"white\" font-weight=\"600\">%s</text>\n",
                escapeXml(qDisplay)));

        // ── Sección conceptos clave ───────────────────────────────────────
        svg.append("<text x=\"32\" y=\"116\" font-family=\"'Segoe UI', Arial, sans-serif\" " +
                   "font-size=\"11\" fill=\"#6b7280\" font-weight=\"700\" " +
                   "letter-spacing=\"1.5\">CONCEPTOS CLAVE</text>\n");
        svg.append("<line x1=\"32\" y1=\"122\" x2=\"210\" y2=\"122\" " +
                   "stroke=\"#3b82f6\" stroke-width=\"2\" stroke-linecap=\"round\"/>\n");

        // Chips de conceptos
        int chipX = 32;
        int chipY = 138;
        int[] chipColorRgb = {0x2563eb, 0x0891b2, 0x7c3aed, 0x059669, 0xd97706, 0xdc2626};

        for (int i = 0; i < concepts.size(); i++) {
            String concept   = concepts.get(i);
            int    chipWidth = Math.max(72, concept.length() * 9 + 22);
            String hexColor  = String.format("#%06x", chipColorRgb[i % chipColorRgb.length]);

            if (chipX + chipWidth > width - 32) {
                chipX = 32;
                chipY += 42;
            }

            svg.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"30\" rx=\"15\" ry=\"15\" " +
                    "fill=\"%s\" filter=\"url(#shadow)\"/>\n",
                    chipX, chipY, chipWidth, hexColor));
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" font-family=\"'Segoe UI', Arial, sans-serif\" " +
                    "font-size=\"12\" fill=\"white\" font-weight=\"500\" text-anchor=\"middle\">%s</text>\n",
                    chipX + chipWidth / 2, chipY + 20, escapeXml(concept)));

            chipX += chipWidth + 12;
        }

        // ── Separador ─────────────────────────────────────────────────────
        int sepY = chipY + 52;
        svg.append(String.format(
                "<line x1=\"32\" y1=\"%d\" x2=\"868\" y2=\"%d\" " +
                "stroke=\"#e2e8f0\" stroke-width=\"1\"/>\n", sepY, sepY));

        // ── Sección fragmentos relevantes ─────────────────────────────────
        int fragLabelY = sepY + 22;
        svg.append(String.format(
                "<text x=\"32\" y=\"%d\" font-family=\"'Segoe UI', Arial, sans-serif\" " +
                "font-size=\"11\" fill=\"#6b7280\" font-weight=\"700\" " +
                "letter-spacing=\"1.5\">FRAGMENTOS RELEVANTES</text>\n", fragLabelY));
        svg.append(String.format(
                "<line x1=\"32\" y1=\"%d\" x2=\"248\" y2=\"%d\" " +
                "stroke=\"#3b82f6\" stroke-width=\"2\" stroke-linecap=\"round\"/>\n",
                fragLabelY + 6, fragLabelY + 6));

        // Tarjetas de fragmentos
        int      cardY        = fragLabelY + 20;
        String[] bgColors     = {"#eff6ff", "#f0fdf4", "#faf5ff"};
        String[] borderColors = {"#3b82f6", "#22c55e", "#a855f7"};

        if (sentences.isEmpty()) {
            sentences = new ArrayList<>();
            sentences.add("No se encontraron fragmentos representativos en el contexto recuperado.");
        }

        for (int i = 0; i < Math.min(sentences.size(), 3); i++) {
            String sent      = sentences.get(i);
            String truncated = sent.length() > 105 ? sent.substring(0, 102) + "..." : sent;
            int    cardH     = 54;

            svg.append(String.format(
                    "<rect x=\"32\" y=\"%d\" width=\"836\" height=\"%d\" rx=\"8\" ry=\"8\" " +
                    "fill=\"%s\" filter=\"url(#shadow)\"/>\n",
                    cardY, cardH, bgColors[i % bgColors.length]));
            // Borde izquierdo de color
            svg.append(String.format(
                    "<rect x=\"32\" y=\"%d\" width=\"4\" height=\"%d\" rx=\"2\" ry=\"2\" " +
                    "fill=\"%s\"/>\n",
                    cardY, cardH, borderColors[i % borderColors.length]));
            // Label "Fragmento N"
            svg.append(String.format(
                    "<text x=\"52\" y=\"%d\" font-family=\"'Segoe UI', Arial, sans-serif\" " +
                    "font-size=\"10\" fill=\"%s\" font-weight=\"700\">Fragmento %d</text>\n",
                    cardY + 18, borderColors[i % borderColors.length], i + 1));
            // Texto del fragmento
            svg.append(String.format(
                    "<text x=\"52\" y=\"%d\" font-family=\"'Segoe UI', Arial, sans-serif\" " +
                    "font-size=\"12\" fill=\"#374151\">%s</text>\n",
                    cardY + 37, escapeXml(truncated)));

            cardY += cardH + 10;
        }

        // ── Footer ────────────────────────────────────────────────────────
        int footerY = height - 28;
        svg.append(String.format(
                "<rect x=\"0\" y=\"%d\" width=\"%d\" height=\"14\" fill=\"#1e3a8a\"/>\n",
                footerY - 14, width));
        svg.append(String.format(
                "<rect x=\"0\" y=\"%d\" width=\"%d\" height=\"28\" " +
                "fill=\"#1e3a8a\" rx=\"0\" ry=\"0\"/>\n", footerY, width));
        // esquinas redondeadas en la parte inferior
        svg.append(String.format(
                "<rect x=\"0\" y=\"%d\" width=\"%d\" height=\"28\" " +
                "fill=\"#1e3a8a\" rx=\"12\" ry=\"12\"/>\n", footerY, width));

        svg.append(String.format(
                "<text x=\"32\" y=\"%d\" font-family=\"'Segoe UI', Arial, sans-serif\" " +
                "font-size=\"11\" fill=\"#93c5fd\">" +
                "DocuCanvas · RAG con Ollama (local) · Infografía generada sin API externa" +
                "</text>\n", footerY + 18));
        svg.append(String.format(
                "<text x=\"868\" y=\"%d\" font-family=\"'Segoe UI', Arial, sans-serif\" " +
                "font-size=\"11\" fill=\"#93c5fd\" text-anchor=\"end\">%s</text>\n",
                footerY + 18,
                java.time.LocalDate.now().toString()));

        svg.append("</svg>");
        return svg.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilidades
    // ─────────────────────────────────────────────────────────────────────────

    private String capitalize(String word) {
        if (word == null || word.isEmpty()) return word;
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
