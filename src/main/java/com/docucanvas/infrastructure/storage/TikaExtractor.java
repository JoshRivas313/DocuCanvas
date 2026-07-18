package com.docucanvas.infrastructure.storage;

import com.docucanvas.domain.exception.TextExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Extrae texto de un archivo, con tracking de página cuando el formato lo
 * permite (PDF). Para formatos sin noción de página (TXT, DOCX, ...) el
 * documento completo se trata como una única "página".
 */
@Component
public class TikaExtractor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TikaExtractor.class);
    private final Tika tika = new Tika();

    /**
     * Extrae el texto del archivo como una lista de páginas.
     *
     * <p>Para PDF se usa PDFBox directamente ({@link PDFTextStripper} acotado
     * página a página vía {@code setStartPage}/{@code setEndPage}), que da
     * límites de página reales y confiables. Para el resto de formatos, sin
     * concepto de página, se devuelve una lista de un solo elemento con todo
     * el texto extraído por Tika.
     *
     * @return lista de textos, uno por página (índice 0 = página 1)
     */
    public List<String> extractPages(byte[] content, String filename, String sourceType) {
        try {
            if ("PDF".equalsIgnoreCase(sourceType)) {
                return extractPdfPages(content);
            }
            log.debug("Extracting text from file: {}, size: {}", filename, content.length);
            return List.of(tika.parseToString(new java.io.ByteArrayInputStream(content)));
        } catch (Exception e) {
            log.error("Failed to extract text from file: {}", filename, e);
            throw new TextExtractionException(filename, e);
        }
    }

    private List<String> extractPdfPages(byte[] content) throws java.io.IOException {
        List<String> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();
            log.debug("Extrayendo PDF de {} páginas", totalPages);
            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document));
            }
        }
        return pages;
    }
}
