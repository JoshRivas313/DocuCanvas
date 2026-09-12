package com.docucanvas.application.service;

import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentStatus;
import com.docucanvas.domain.repository.DocumentRepository;
import com.docucanvas.infrastructure.config.RagProperties;
import com.docucanvas.infrastructure.storage.TikaExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Invariantes de la ingesta. */
@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionService — estado final del documento")
class IngestionServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private TikaExtractor tikaExtractor;
    @Mock private VectorStore vectorStore;

    private static RagProperties props() {
        return new RagProperties(
                new RagProperties.Chunking(200, 50, 5, 10000, true),
                new RagProperties.Retrieval(5, 20, 0.3, 0.0),
                new RagProperties.Generation("m", 0.7, 400, 3.7, 25.0, 5.0, 15, 30, 150),
                new RagProperties.Visual(1024, 1024));
    }

    private Document pending(UUID id) {
        return Document.builder()
                .id(id).title("doc.pdf").sourceType("PDF")
                .status(DocumentStatus.PENDING).chunkCount(0).tags(List.of())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Un PDF sin texto extraíble se marca FAILED, no READY con 0 chunks")
    void pdfSinTextoExtraibleSeMarcaFailed() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.of(pending(id)));
        // Un PDF escaneado: PDFBox devuelve páginas, pero todas vacías.
        when(tikaExtractor.extractPages(any(), anyString(), anyString()))
                .thenReturn(List.of("", "   ", ""));

        new IngestionService(documentRepository, tikaExtractor, vectorStore, props())
                .processIngestion(id, new byte[]{1, 2, 3}, "escaneado.pdf");

        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository, atLeastOnce()).save(saved.capture());

        assertThat(saved.getAllValues().get(saved.getAllValues().size() - 1).getStatus())
                .as("marcarlo READY con 0 chunks haría que el documento parezca listo "
                        + "y luego ninguna pregunta encontrara nada")
                .isEqualTo(DocumentStatus.FAILED);

        // Y no se llama al vector store con una lista vacía.
        verify(vectorStore, never()).add(any());
    }

    @Test
    @DisplayName("Un documento con texto sí se indexa y queda READY")
    void documentoConTextoQuedaReady() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.of(pending(id)));
        when(tikaExtractor.extractPages(any(), anyString(), anyString()))
                .thenReturn(List.of("El servicio de conciliacion consume eventos desde una cola Kafka "
                        + "y contrasta cada movimiento con el extracto bancario cada 24 horas."));

        new IngestionService(documentRepository, tikaExtractor, vectorStore, props())
                .processIngestion(id, new byte[]{1, 2, 3}, "bueno.pdf");

        verify(vectorStore).add(any());
        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().get(saved.getAllValues().size() - 1).getStatus())
                .isEqualTo(DocumentStatus.READY);
    }
}
