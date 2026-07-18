package com.docucanvas.infrastructure.storage;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link TikaExtractor#extractPages}, en particular el tracking real
 * de página para PDF (vía PDFBox), verificado contra un PDF construido en el
 * propio test — no un fixture externo.
 */
@DisplayName("TikaExtractor — Extracción de texto con tracking de página")
class TikaExtractorTest {

    private final TikaExtractor extractor = new TikaExtractor();

    private byte[] buildPdf(String... pageTexts) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    cs.beginText();
                    cs.setFont(font, 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("Un PDF de 3 páginas devuelve 3 elementos, con el texto correcto en cada índice")
    void pdfDeTresPaginasDevuelveTresElementos() throws IOException {
        byte[] pdf = buildPdf("Contenido de la pagina uno", "Contenido de la pagina dos", "Contenido de la pagina tres");

        List<String> pages = extractor.extractPages(pdf, "test.pdf", "PDF");

        assertThat(pages).hasSize(3);
        assertThat(pages.get(0)).contains("pagina uno");
        assertThat(pages.get(1)).contains("pagina dos");
        assertThat(pages.get(2)).contains("pagina tres");
    }

    @Test
    @DisplayName("Un formato sin noción de página (TXT) devuelve una única 'página' con todo el texto")
    void formatoSinPaginaDevuelveUnaSolaPagina() {
        byte[] txt = "Este es el contenido completo de un archivo de texto plano.".getBytes(StandardCharsets.UTF_8);

        List<String> pages = extractor.extractPages(txt, "notas.txt", "TXT");

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0)).contains("contenido completo");
    }
}
