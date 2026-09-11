package com.docucanvas.application.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio responsable de generar una representación visual del contexto
 * recuperado por el pipeline RAG.
 *
 * <p><b>Estrategia de reemplazo de DALL-E:</b> en lugar de llamar a una API
 * externa de generación de imágenes (costosa y dependiente de red), este
 * servicio genera un diagrama SVG local a partir del <b>tema detectado</b> en
 * el contexto recuperado — no una infografía genérica, sino una de 8
 * plantillas visualmente distintas (blockchain, nube/K8s, cadena de
 * suministro, API/REST, gobierno de TI, proceso, corporativo, genérica),
 * cada una con los conceptos reales del documento como etiquetas.
 *
 * <p>El SVG producido se devuelve como Data URL {@code data:image/svg+xml;base64,...}
 * que el navegador puede renderizar directamente sin necesidad de servidor
 * externo ni almacenamiento adicional.
 */
@Service
public class ImageGenerationService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ImageGenerationService.class);

    private static final int WIDTH = 900;
    private static final int HEIGHT = 620;
    private static final String HEADER_FROM = "#1e3a8a";
    private static final String HEADER_TO = "#2563eb";

    /** Tema visual detectado a partir del contexto recuperado. */
    private enum Topic { BLOCKCHAIN, CLOUD_INFRA, SUPPLY_CHAIN, API_REST, IT_GOVERNANCE, PROCESS_FLOW, CORPORATE, GENERIC }

    private static final Map<Topic, Set<String>> TOPIC_KEYWORDS = Map.of(
            Topic.BLOCKCHAIN, Set.of("blockchain", "hash", "bloque", "criptográfic", "criptografía",
                    "smart contract", "contrato inteligente", "transacción", "hardhat", "ethereum", "nodo", "cripto"),
            Topic.CLOUD_INFRA, Set.of("kubernetes", "docker", "contenedor", "cluster", "aws", "azure",
                    "cloud", "nube", "pod", "microservicio", "despliegue", "servidor"),
            Topic.SUPPLY_CHAIN, Set.of("cadena de suministro", "logística", "inventario", "almacén",
                    "proveedor", "distribución", "transporte", "trazabilidad"),
            Topic.API_REST, Set.of("api", "rest", "endpoint", "http", "json", "request", "response",
                    "integración", "webhook"),
            Topic.IT_GOVERNANCE, Set.of("gobierno de ti", "cobit", "itil", "cmmi", "iso", "kpi",
                    "gobernanza", "auditoría", "cumplimiento"),
            Topic.PROCESS_FLOW, Set.of("proceso", "flujo", "etapa", "fase", "procedimiento", "workflow"),
            Topic.CORPORATE, Set.of("empresa", "organización", "corporativ", "negocio", "compañía", "institución")
    );

    private final ConceptExtractor conceptExtractor;

    public ImageGenerationService(ConceptExtractor conceptExtractor) {
        this.conceptExtractor = conceptExtractor;
    }

    /**
     * Genera un Data URL SVG a partir de la pregunta y el contexto documental,
     * eligiendo automáticamente la plantilla visual más afín al tema detectado.
     *
     * @param visualPrompt prompt derivado por el LLM del contexto; fuente preferente
     *                     para las etiquetas del diagrama
     * @param question     pregunta original del usuario
     * @param context      contexto documental recuperado del VectorStore
     * @return Data URL {@code data:image/svg+xml;base64,...} listo para {@code <img src="...">}
     */
    public String generateImageDataUrl(String visualPrompt, String question, String context) {
        // El prompt visual, cuando existe, es la interpretacion que el LLM hizo del
        // contexto recuperado: es mejor fuente para las etiquetas del diagrama que la
        // frecuencia de palabras del texto crudo, que no distingue lo relevante de lo
        // meramente repetido. Solo se cae al contexto si el modelo no lo produjo.
        boolean hasVisualPrompt = visualPrompt != null && !visualPrompt.isBlank();
        String conceptSource = hasVisualPrompt ? visualPrompt : context;

        List<String> concepts = conceptExtractor.extractKeyConcepts(conceptSource, 6);
        List<String> sentences = hasVisualPrompt
                ? conceptExtractor.extractKeySentences(visualPrompt, 3)
                : conceptExtractor.extractKeySentences(context, 3);
        Topic topic = detectTopic(conceptSource, concepts);

        log.info("[IMAGE] Respaldo local: diagrama SVG tema={} origen={} para: {}",
                topic, hasVisualPrompt ? "visualPrompt" : "contexto-crudo", question);

        String svg = buildDiagram(topic, question, concepts, sentences);
        String base64 = Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        String dataUrl = "data:image/svg+xml;base64," + base64;

        log.debug("SVG generado ({} chars, tema={}, {} conceptos)", svg.length(), topic, concepts.size());
        return dataUrl;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Detección de tema
    // ─────────────────────────────────────────────────────────────────────────

    private Topic detectTopic(String context, List<String> concepts) {
        if (context == null || context.isBlank()) return Topic.GENERIC;
        String haystack = (context + " " + String.join(" ", concepts)).toLowerCase();

        Topic best = Topic.GENERIC;
        int bestScore = 0;
        for (Map.Entry<Topic, Set<String>> entry : TOPIC_KEYWORDS.entrySet()) {
            int score = 0;
            for (String kw : entry.getValue()) {
                if (haystack.contains(kw)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = entry.getKey();
            }
        }
        return bestScore > 0 ? best : Topic.GENERIC;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construcción del SVG
    // ─────────────────────────────────────────────────────────────────────────

    private String buildDiagram(Topic topic, String question, List<String> concepts, List<String> sentences) {
        StringBuilder svg = new StringBuilder();
        openSvgAndChrome(svg, question);

        switch (topic) {
            case BLOCKCHAIN -> buildBlockchainBody(svg, concepts);
            case CLOUD_INFRA -> buildCloudBody(svg, concepts);
            case SUPPLY_CHAIN -> buildSupplyChainBody(svg, concepts);
            case API_REST -> buildApiBody(svg, concepts);
            case IT_GOVERNANCE -> buildGovernanceBody(svg, concepts);
            case PROCESS_FLOW -> buildProcessFlowBody(svg, concepts);
            case CORPORATE -> buildCorporateBody(svg, concepts);
            default -> buildGenericBody(svg, concepts, sentences);
        }

        closeChrome(svg, topic);
        svg.append("</svg>");
        return svg.toString();
    }

    /** Cabecera (fondo, gradientes, título con la pregunta) compartida por todas las plantillas. */
    private void openSvgAndChrome(StringBuilder svg, String question) {
        svg.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">%n",
                WIDTH, HEIGHT, WIDTH, HEIGHT));

        svg.append("<defs>\n");
        svg.append("  <linearGradient id=\"headerGrad\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"0\">\n");
        svg.append("    <stop offset=\"0%\" stop-color=\"").append(HEADER_FROM).append("\"/>\n");
        svg.append("    <stop offset=\"100%\" stop-color=\"").append(HEADER_TO).append("\"/>\n");
        svg.append("  </linearGradient>\n");
        svg.append("  <linearGradient id=\"bgGrad\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">\n");
        svg.append("    <stop offset=\"0%\" stop-color=\"#f8faff\"/>\n");
        svg.append("    <stop offset=\"100%\" stop-color=\"#eff6ff\"/>\n");
        svg.append("  </linearGradient>\n");
        svg.append("  <filter id=\"shadow\"><feDropShadow dx=\"0\" dy=\"2\" stdDeviation=\"3\" flood-opacity=\"0.12\"/></filter>\n");
        svg.append("</defs>\n");

        svg.append(String.format("<rect width=\"%d\" height=\"%d\" fill=\"url(#bgGrad)\" rx=\"12\" ry=\"12\"/>%n", WIDTH, HEIGHT));
        svg.append("<rect x=\"0\" y=\"0\" width=\"900\" height=\"90\" fill=\"url(#headerGrad)\" rx=\"12\" ry=\"12\"/>\n");
        svg.append("<rect x=\"0\" y=\"70\" width=\"900\" height=\"20\" fill=\"url(#headerGrad)\"/>\n");
        svg.append("<circle cx=\"42\" cy=\"45\" r=\"14\" fill=\"none\" stroke=\"white\" stroke-width=\"2.5\" opacity=\"0.8\"/>\n");
        svg.append("<line x1=\"52\" y1=\"55\" x2=\"62\" y2=\"65\" stroke=\"white\" stroke-width=\"2.5\" stroke-linecap=\"round\" opacity=\"0.8\"/>\n");
        svg.append("<text x=\"80\" y=\"38\" font-family=\"'Segoe UI', Arial, sans-serif\" font-size=\"11\" fill=\"#bfdbfe\" " +
                "font-weight=\"400\" letter-spacing=\"1.5\">CONSULTA DOCUMENTAL</text>\n");

        String qDisplay = question.length() > 78 ? question.substring(0, 75) + "..." : question;
        svg.append(String.format(
                "<text x=\"80\" y=\"63\" font-family=\"'Segoe UI', Arial, sans-serif\" font-size=\"15\" fill=\"white\" " +
                "font-weight=\"600\">%s</text>%n", escapeXml(qDisplay)));
    }

    private void closeChrome(StringBuilder svg, Topic topic) {
        int footerY = HEIGHT - 28;
        svg.append(String.format("<rect x=\"0\" y=\"%d\" width=\"%d\" height=\"14\" fill=\"#1e3a8a\"/>%n", footerY - 14, WIDTH));
        svg.append(String.format("<rect x=\"0\" y=\"%d\" width=\"%d\" height=\"28\" fill=\"#1e3a8a\" rx=\"12\" ry=\"12\"/>%n", footerY, WIDTH));
        svg.append(String.format(
                "<text x=\"32\" y=\"%d\" font-family=\"'Segoe UI', Arial, sans-serif\" font-size=\"11\" fill=\"#93c5fd\">" +
                "DocuCanvas · Diagrama %s generado localmente sin API externa</text>%n", footerY + 18, topicLabel(topic)));
        svg.append(String.format(
                "<text x=\"868\" y=\"%d\" font-family=\"'Segoe UI', Arial, sans-serif\" font-size=\"11\" fill=\"#93c5fd\" " +
                "text-anchor=\"end\">%s</text>%n", footerY + 18, java.time.LocalDate.now().toString()));
    }

    private String topicLabel(Topic topic) {
        return switch (topic) {
            case BLOCKCHAIN -> "Blockchain";
            case CLOUD_INFRA -> "Cloud/Infraestructura";
            case SUPPLY_CHAIN -> "Cadena de suministro";
            case API_REST -> "API/REST";
            case IT_GOVERNANCE -> "Gobierno de TI";
            case PROCESS_FLOW -> "Proceso";
            case CORPORATE -> "Corporativo";
            default -> "Conceptual";
        };
    }

    // ── Plantilla: BLOCKCHAIN — cadena de bloques conectados ──────────────────
    private void buildBlockchainBody(StringBuilder svg, List<String> concepts) {
        sectionTitle(svg, "CADENA DE BLOQUES");
        String[] colors = {"#2563eb", "#0891b2", "#7c3aed", "#059669", "#d97706"};
        int n = Math.min(Math.max(concepts.size(), 3), 5);
        int blockW = 130, blockH = 100, gap = 34;
        int totalW = n * blockW + (n - 1) * gap;
        int startX = (WIDTH - totalW) / 2;
        int y = 220;

        for (int i = 0; i < n; i++) {
            int x = startX + i * (blockW + gap);
            String color = colors[i % colors.length];
            if (i > 0) {
                int prevX = startX + (i - 1) * (blockW + gap) + blockW;
                svg.append(String.format(
                        "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#94a3b8\" stroke-width=\"3\" " +
                        "stroke-dasharray=\"6,4\"/>%n", prevX, y + blockH / 2, x, y + blockH / 2));
            }
            svg.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"10\" ry=\"10\" fill=\"%s\" " +
                    "filter=\"url(#shadow)\"/>%n", x, y, blockW, blockH, color));
            svg.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"14\" rx=\"3\" ry=\"3\" fill=\"rgba(255,255,255,0.25)\"/>%n",
                    x + 14, y + 14, blockW - 28));
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" font-family=\"'Segoe UI'\" font-size=\"10\" fill=\"white\" " +
                    "opacity=\"0.75\">BLOQUE %02d</text>%n", x + 14, y + 46, i + 1));
            String label = concepts.size() > i ? concepts.get(i) : "Dato " + (i + 1);
            svg.append(wrappedLabel(x + blockW / 2, y + 70, blockW - 16, label, "white", 11, true));
        }
    }

    // ── Plantilla: CLOUD/K8S/AWS — topología en estrella ──────────────────────
    private void buildCloudBody(StringBuilder svg, List<String> concepts) {
        sectionTitle(svg, "INFRAESTRUCTURA CLOUD");
        int cx = WIDTH / 2, cy = 300, r = 46;
        svg.append(String.format(
                "<circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"#0891b2\" filter=\"url(#shadow)\"/>%n", cx, cy, r));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-family=\"'Segoe UI'\" font-size=\"10\" " +
                "fill=\"white\" font-weight=\"700\">CLUSTER</text>%n", cx, cy - 4));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-family=\"'Segoe UI'\" font-size=\"9\" " +
                "fill=\"white\" opacity=\"0.8\">Control Plane</text>%n", cx, cy + 12));

        String[] colors = {"#2563eb", "#7c3aed", "#059669", "#d97706"};
        int n = Math.min(Math.max(concepts.size(), 3), 4);
        double radius = 190;
        for (int i = 0; i < n; i++) {
            double angle = (2 * Math.PI * i / n) - Math.PI / 2;
            int nx = cx + (int) (radius * Math.cos(angle));
            int ny = cy + (int) (radius * Math.sin(angle) * 0.55);
            svg.append(String.format(
                    "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#cbd5e1\" stroke-width=\"2\"/>%n",
                    cx, cy, nx, ny));
            svg.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"96\" height=\"52\" rx=\"8\" ry=\"8\" fill=\"%s\" " +
                    "filter=\"url(#shadow)\"/>%n", nx - 48, ny - 26, colors[i % colors.length]));
            String label = concepts.size() > i ? concepts.get(i) : "Nodo " + (i + 1);
            svg.append(wrappedLabel(nx, ny, 88, label, "white", 11, true));
        }
    }

    // ── Plantilla: CADENA DE SUMINISTRO — flujo horizontal ────────────────────
    private void buildSupplyChainBody(StringBuilder svg, List<String> concepts) {
        sectionTitle(svg, "FLUJO DE LA CADENA");
        String[] stageNames = {"Origen", "Proceso", "Distribución", "Destino"};
        String[] colors = {"#2563eb", "#0891b2", "#059669", "#d97706"};
        int n = 4, boxW = 170, gap = 30;
        int totalW = n * boxW + (n - 1) * gap;
        int startX = (WIDTH - totalW) / 2, y = 230, boxH = 90;

        for (int i = 0; i < n; i++) {
            int x = startX + i * (boxW + gap);
            if (i > 0) {
                int midY = y + boxH / 2;
                svg.append(String.format(
                        "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#94a3b8\" stroke-width=\"3\"/>%n",
                        x - gap + 6, midY, x - 6, midY));
                svg.append(String.format(
                        "<path d=\"M %d %d L %d %d L %d %d Z\" fill=\"#94a3b8\"/>%n",
                        x - 6, midY - 6, x + 4, midY, x - 6, midY + 6));
            }
            svg.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"10\" ry=\"10\" fill=\"%s\" " +
                    "filter=\"url(#shadow)\"/>%n", x, y, boxW, boxH, colors[i]));
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-family=\"'Segoe UI'\" font-size=\"9\" " +
                    "fill=\"white\" opacity=\"0.75\" letter-spacing=\"1\">%s</text>%n",
                    x + boxW / 2, y + 22, stageNames[i].toUpperCase()));
            String label = concepts.size() > i ? concepts.get(i) : stageNames[i];
            svg.append(wrappedLabel(x + boxW / 2, y + 55, boxW - 20, label, "white", 12, true));
        }
    }

    // ── Plantilla: API/REST — cliente-servidor ─────────────────────────────────
    private void buildApiBody(StringBuilder svg, List<String> concepts) {
        sectionTitle(svg, "INTERACCIÓN API");
        int y = 240, boxW = 220, boxH = 130;
        int clientX = 90, serverX = WIDTH - 90 - boxW;

        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"10\" ry=\"10\" fill=\"#2563eb\" " +
                "filter=\"url(#shadow)\"/>%n", clientX, y, boxW, boxH));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-family=\"'Segoe UI'\" font-size=\"13\" " +
                "fill=\"white\" font-weight=\"700\">CLIENTE</text>%n", clientX + boxW / 2, y + 34));

        svg.append(String.format(
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"10\" ry=\"10\" fill=\"#7c3aed\" " +
                "filter=\"url(#shadow)\"/>%n", serverX, y, boxW, boxH));
        svg.append(String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-family=\"'Segoe UI'\" font-size=\"13\" " +
                "fill=\"white\" font-weight=\"700\">SERVIDOR</text>%n", serverX + boxW / 2, y + 34));

        String[] verbs = {"GET", "POST", "200 OK"};
        int arrowY = y + 58;
        for (int i = 0; i < 3; i++) {
            int ly = arrowY + i * 26;
            boolean toServer = i < 2;
            int x1 = toServer ? clientX + boxW : serverX;
            int x2 = toServer ? serverX : clientX + boxW;
            svg.append(String.format(
                    "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#059669\" stroke-width=\"2\"/>%n", x1, ly, x2, ly));
            int ax = toServer ? x2 - 8 : x2 + 8;
            svg.append(String.format("<path d=\"M %d %d L %d %d L %d %d Z\" fill=\"#059669\"/>%n",
                    ax, ly - 5, toServer ? x2 : x2, ly, ax, ly + 5));
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-family=\"monospace\" font-size=\"10\" " +
                    "fill=\"#374151\" font-weight=\"700\">%s</text>%n", WIDTH / 2, ly - 6, verbs[i]));
        }

        int chipY = y + boxH + 30;
        renderConceptChips(svg, concepts, chipY);
    }

    // ── Plantilla: GOBIERNO DE TI — pirámide jerárquica ────────────────────────
    private void buildGovernanceBody(StringBuilder svg, List<String> concepts) {
        sectionTitle(svg, "MODELO DE GOBIERNO");
        String[] levels = {"Estrategia", "Gestión", "Operación"};
        String[] colors = {"#1e3a8a", "#2563eb", "#60a5fa"};
        int topY = 190, levelH = 62;
        int maxW = 620;

        for (int i = 0; i < 3; i++) {
            int w = maxW - i * 170;
            int x = (WIDTH - w) / 2;
            int y = topY + i * (levelH + 6);
            svg.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"8\" ry=\"8\" fill=\"%s\" " +
                    "filter=\"url(#shadow)\"/>%n", x, y, w, levelH, colors[i]));
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-family=\"'Segoe UI'\" font-size=\"9\" " +
                    "fill=\"white\" opacity=\"0.7\" letter-spacing=\"1.5\">%s</text>%n",
                    WIDTH / 2, y + 20, levels[i].toUpperCase()));
            String label = concepts.size() > i ? concepts.get(i) : levels[i];
            svg.append(wrappedLabel(WIDTH / 2, y + 42, w - 40, label, "white", 12, true));
        }
    }

    // ── Plantilla: PROCESO — flowchart vertical con decisión ──────────────────
    private void buildProcessFlowBody(StringBuilder svg, List<String> concepts) {
        sectionTitle(svg, "FLUJO DEL PROCESO");
        int cx = WIDTH / 2;
        int y = 200, stepH = 56, gap = 26, boxW = 260;
        String[] colors = {"#2563eb", "#0891b2", "#d97706", "#059669"};
        int n = Math.min(Math.max(concepts.size(), 3), 4);

        for (int i = 0; i < n; i++) {
            boolean isDecision = (i == n - 2 && n > 2);
            if (isDecision) {
                int size = 84;
                svg.append(String.format(
                        "<path d=\"M %d %d L %d %d L %d %d L %d %d Z\" fill=\"%s\" filter=\"url(#shadow)\"/>%n",
                        cx, y, cx + size / 2, y + size / 2, cx, y + size, cx - size / 2, y + size / 2,
                        colors[i % colors.length]));
                String label = concepts.size() > i ? concepts.get(i) : "¿Cumple?";
                svg.append(wrappedLabel(cx, y + size / 2, size - 10, label, "white", 10, true));
                y += size + gap;
            } else {
                svg.append(String.format(
                        "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"28\" ry=\"28\" fill=\"%s\" " +
                        "filter=\"url(#shadow)\"/>%n", cx - boxW / 2, y, boxW, stepH, colors[i % colors.length]));
                String label = concepts.size() > i ? concepts.get(i) : "Paso " + (i + 1);
                svg.append(wrappedLabel(cx, y + stepH / 2 + 4, boxW - 20, label, "white", 12, true));
                y += stepH + gap;
            }
            if (i < n - 1) {
                svg.append(String.format(
                        "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#94a3b8\" stroke-width=\"2.5\"/>%n",
                        cx, y - gap, cx, y - 4));
                svg.append(String.format("<path d=\"M %d %d L %d %d L %d %d Z\" fill=\"#94a3b8\"/>%n",
                        cx - 5, y - 10, cx + 5, y - 10, cx, y));
            }
        }
    }

    // ── Plantilla: CORPORATIVO — estructura de edificio/departamentos ─────────
    private void buildCorporateBody(StringBuilder svg, List<String> concepts) {
        sectionTitle(svg, "ESTRUCTURA ORGANIZACIONAL");
        int roofY = 190;
        svg.append(String.format(
                "<path d=\"M %d %d L %d %d L %d %d Z\" fill=\"#1e3a8a\"/>%n",
                WIDTH / 2, roofY, WIDTH / 2 - 130, roofY + 50, WIDTH / 2 + 130, roofY + 50));

        String[] colors = {"#2563eb", "#0891b2", "#7c3aed", "#059669"};
        int n = Math.min(Math.max(concepts.size(), 3), 4);
        int boxW = 130, boxH = 130, gap = 20;
        int totalW = n * boxW + (n - 1) * gap;
        int startX = (WIDTH - totalW) / 2, y = roofY + 50;

        for (int i = 0; i < n; i++) {
            int x = startX + i * (boxW + gap);
            svg.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"%s\" filter=\"url(#shadow)\"/>%n",
                    x, y, boxW, boxH, colors[i % colors.length]));
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 2; col++) {
                    svg.append(String.format(
                            "<rect x=\"%d\" y=\"%d\" width=\"20\" height=\"24\" fill=\"rgba(255,255,255,0.3)\"/>%n",
                            x + 18 + col * 55, y + 16 + row * 50));
                }
            }
            String label = concepts.size() > i ? concepts.get(i) : "Depto. " + (i + 1);
            svg.append(wrappedLabel(x + boxW / 2, y + boxH + 20, boxW, label, "#1e293b", 11, false));
        }
    }

    // ── Plantilla: GENÉRICA — chips + fragmentos (diseño original) ────────────
    private void buildGenericBody(StringBuilder svg, List<String> concepts, List<String> sentences) {
        svg.append("<text x=\"32\" y=\"116\" font-family=\"'Segoe UI', Arial, sans-serif\" font-size=\"11\" " +
                "fill=\"#6b7280\" font-weight=\"700\" letter-spacing=\"1.5\">CONCEPTOS CLAVE</text>\n");
        svg.append("<line x1=\"32\" y1=\"122\" x2=\"210\" y2=\"122\" stroke=\"#3b82f6\" stroke-width=\"2\" " +
                "stroke-linecap=\"round\"/>\n");
        renderConceptChips(svg, concepts, 138);

        int sepY = 240;
        svg.append(String.format(
                "<line x1=\"32\" y1=\"%d\" x2=\"868\" y2=\"%d\" stroke=\"#e2e8f0\" stroke-width=\"1\"/>%n", sepY, sepY));

        int fragLabelY = sepY + 22;
        svg.append(String.format(
                "<text x=\"32\" y=\"%d\" font-family=\"'Segoe UI', Arial, sans-serif\" font-size=\"11\" " +
                "fill=\"#6b7280\" font-weight=\"700\" letter-spacing=\"1.5\">FRAGMENTOS RELEVANTES</text>%n", fragLabelY));
        svg.append(String.format(
                "<line x1=\"32\" y1=\"%d\" x2=\"248\" y2=\"%d\" stroke=\"#3b82f6\" stroke-width=\"2\" " +
                "stroke-linecap=\"round\"/>%n", fragLabelY + 6, fragLabelY + 6));

        int cardY = fragLabelY + 20;
        String[] bgColors = {"#eff6ff", "#f0fdf4", "#faf5ff"};
        String[] borderColors = {"#3b82f6", "#22c55e", "#a855f7"};
        List<String> toRender = sentences.isEmpty()
                ? List.of("No se encontraron fragmentos representativos en el contexto recuperado.")
                : sentences;

        for (int i = 0; i < Math.min(toRender.size(), 3); i++) {
            String truncated = toRender.get(i).length() > 105 ? toRender.get(i).substring(0, 102) + "..." : toRender.get(i);
            int cardH = 54;
            svg.append(String.format(
                    "<rect x=\"32\" y=\"%d\" width=\"836\" height=\"%d\" rx=\"8\" ry=\"8\" fill=\"%s\" " +
                    "filter=\"url(#shadow)\"/>%n", cardY, cardH, bgColors[i % bgColors.length]));
            svg.append(String.format(
                    "<rect x=\"32\" y=\"%d\" width=\"4\" height=\"%d\" rx=\"2\" ry=\"2\" fill=\"%s\"/>%n",
                    cardY, cardH, borderColors[i % borderColors.length]));
            svg.append(String.format(
                    "<text x=\"52\" y=\"%d\" font-family=\"'Segoe UI', Arial, sans-serif\" font-size=\"10\" " +
                    "fill=\"%s\" font-weight=\"700\">Fragmento %d</text>%n",
                    cardY + 18, borderColors[i % borderColors.length], i + 1));
            svg.append(String.format(
                    "<text x=\"52\" y=\"%d\" font-family=\"'Segoe UI', Arial, sans-serif\" font-size=\"12\" " +
                    "fill=\"#374151\">%s</text>%n", cardY + 37, escapeXml(truncated)));
            cardY += cardH + 10;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilidades compartidas entre plantillas
    // ─────────────────────────────────────────────────────────────────────────

    private void sectionTitle(StringBuilder svg, String title) {
        svg.append(String.format(
                "<text x=\"%d\" y=\"116\" text-anchor=\"middle\" font-family=\"'Segoe UI', Arial, sans-serif\" " +
                "font-size=\"11\" fill=\"#6b7280\" font-weight=\"700\" letter-spacing=\"2\">%s</text>%n",
                WIDTH / 2, title));
        svg.append(String.format(
                "<line x1=\"%d\" y1=\"126\" x2=\"%d\" y2=\"126\" stroke=\"#3b82f6\" stroke-width=\"2\" " +
                "stroke-linecap=\"round\"/>%n", WIDTH / 2 - 70, WIDTH / 2 + 70));
    }

    private void renderConceptChips(StringBuilder svg, List<String> concepts, int chipY) {
        int chipX = 32;
        int y = chipY;
        int[] chipColorRgb = {0x2563eb, 0x0891b2, 0x7c3aed, 0x059669, 0xd97706, 0xdc2626};
        for (int i = 0; i < concepts.size(); i++) {
            String concept = concepts.get(i);
            int chipWidth = Math.max(72, concept.length() * 9 + 22);
            String hexColor = String.format("#%06x", chipColorRgb[i % chipColorRgb.length]);
            if (chipX + chipWidth > WIDTH - 32) {
                chipX = 32;
                y += 42;
            }
            svg.append(String.format(
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"30\" rx=\"15\" ry=\"15\" fill=\"%s\" " +
                    "filter=\"url(#shadow)\"/>%n", chipX, y, chipWidth, hexColor));
            svg.append(String.format(
                    "<text x=\"%d\" y=\"%d\" font-family=\"'Segoe UI', Arial, sans-serif\" font-size=\"12\" " +
                    "fill=\"white\" font-weight=\"500\" text-anchor=\"middle\">%s</text>%n",
                    chipX + chipWidth / 2, y + 20, escapeXml(concept)));
            chipX += chipWidth + 12;
        }
    }

    /** Texto centrado en una sola línea, truncado si excede el ancho disponible. */
    private String wrappedLabel(int cx, int y, int maxWidth, String text, String color, int fontSize, boolean bold) {
        int maxChars = Math.max(4, maxWidth / (fontSize / 2 + 2));
        String display = text.length() > maxChars ? text.substring(0, maxChars - 1) + "…" : text;
        return String.format(
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-family=\"'Segoe UI', Arial, sans-serif\" " +
                "font-size=\"%d\" fill=\"%s\"%s>%s</text>%n",
                cx, y, fontSize, color, bold ? " font-weight=\"700\"" : "", escapeXml(display));
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
