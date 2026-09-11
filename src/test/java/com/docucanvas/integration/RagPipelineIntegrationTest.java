package com.docucanvas.integration;

import com.docucanvas.BaseIntegrationTest;
import com.docucanvas.application.port.out.ChunkReadPort;
import com.docucanvas.application.rag.RagRetriever;
import com.docucanvas.application.rag.RetrievalResult;
import com.docucanvas.application.usecase.UploadDocumentUseCase;
import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentStatus;
import com.docucanvas.domain.repository.DocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test 1 — Pipeline RAG de extremo a extremo contra PostgreSQL + pgvector reales.
 *
 * <p>Recorre la cadena completa sin simular ninguna pieza propia:
 * <pre>
 * PDF → extracción (PDFBox) → chunking (TokenTextSplitter) → embeddings
 *     → PGVector → retrieval semántico → contexto
 * </pre>
 *
 * <p>Lo único sustituido es el modelo de embeddings, por
 * {@link DeterministicEmbeddingModel}: el objetivo es verificar el cableado del
 * pipeline, no la calidad semántica de un modelo concreto, y depender de Ollama
 * haría fallar el test en cualquier máquina sin los modelos descargados.
 */
@Import(RagPipelineIntegrationTest.DeterministicEmbeddingConfig.class)
@DisplayName("Integración — Pipeline RAG completo sobre PGVector real")
class RagPipelineIntegrationTest extends BaseIntegrationTest {

    @TestConfiguration
    static class DeterministicEmbeddingConfig {
        @Bean
        @Primary
        EmbeddingModel deterministicEmbeddingModel() {
            return new DeterministicEmbeddingModel();
        }
    }

    @Autowired private UploadDocumentUseCase uploadDocumentUseCase;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ChunkReadPort chunkReadPort;
    @Autowired private RagRetriever ragRetriever;

    @Test
    @DisplayName("Un PDF real recorre extracción, chunking, embeddings, PGVector y retrieval")
    void pdfRecorreElPipelineCompletoYEsRecuperable() throws Exception {
        byte[] pdf = buildPdf(List.of(
                "El servicio de conciliacion consume eventos desde la cola Kafka "
                        + "y contrasta cada movimiento con el extracto bancario.",
                "La base de datos principal es PostgreSQL con replicacion sincrona "
                        + "hacia una region secundaria para tolerancia a fallos."));

        // ── Ingesta: entra por el mismo caso de uso que usa el endpoint HTTP ──
        Document saved = uploadDocumentUseCase.execute(
                new MockMultipartFile("file", "arquitectura.pdf", "application/pdf", pdf),
                "Arquitectura de pagos");

        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.PENDING);

        // La ingesta es @Async: se espera al estado terminal, no a un sleep fijo.
        Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(
                        documentRepository.findById(saved.getId()).orElseThrow().getStatus())
                        .isEqualTo(DocumentStatus.READY));

        // ── Chunking + embeddings + persistencia ──────────────────────────
        int chunks = chunkReadPort.countByDocument(saved.getId().toString());
        assertThat(chunks)
                .as("el PDF debe haber producido al menos un chunk vectorizado en PGVector")
                .isPositive();
        assertThat(documentRepository.findById(saved.getId()).orElseThrow().getChunkCount())
                .isEqualTo(chunks);

        // ── Retrieval semántico sobre lo que se acaba de indexar ──────────
        RetrievalResult result = ragRetriever.retrieve(
                "Como se concilian los movimientos con el banco", 3, saved.getId().toString());

        assertThat(result.documents())
                .as("la búsqueda semántica debe recuperar evidencia del documento indexado")
                .isNotEmpty();

        // El contexto recuperado procede del PDF, con su metadata de trazabilidad.
        org.springframework.ai.document.Document top = result.documents().get(0);
        assertThat(top.getText()).isNotBlank();
        assertThat(top.getMetadata()).containsEntry("source", "arquitectura.pdf");
        assertThat(top.getMetadata()).containsKey("page");
        assertThat(top.getMetadata()).containsEntry("documentId", saved.getId().toString());
    }

    @Test
    @DisplayName("El filtro por documento aísla el retrieval: no se cuela contexto de otros documentos")
    void elFiltroPorDocumentoAislaElRetrieval() {
        RetrievalResult result = ragRetriever.retrieve(
                "cualquier pregunta", 3, java.util.UUID.randomUUID().toString());

        assertThat(result.documents())
                .as("un documentId inexistente no puede devolver contexto de otros documentos")
                .isEmpty();
    }

    /** Construye un PDF real en memoria, una página por texto. */
    private byte[] buildPdf(List<String> pages) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(40, 700);
                    // showText no admite saltos de linea: una linea por fragmento.
                    for (String line : splitEvery(text, 70)) {
                        cs.showText(line);
                        cs.newLineAtOffset(0, -16);
                    }
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private List<String> splitEvery(String text, int size) {
        List<String> parts = new java.util.ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            parts.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return parts;
    }
}
