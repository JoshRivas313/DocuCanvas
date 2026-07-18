package com.docucanvas.application.usecase;

import com.docucanvas.application.service.IngestionService;
import com.docucanvas.domain.exception.UnsupportedFileTypeException;
import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Tests unitarios de {@link ImportTextUseCase}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImportTextUseCase — Ingesta de texto directo")
class ImportTextUseCaseTest {

    @Mock private com.docucanvas.domain.repository.DocumentRepository documentRepository;
    @Mock private IngestionService ingestionService;

    private ImportTextUseCase useCase() {
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        return new ImportTextUseCase(documentRepository, ingestionService);
    }

    @Test
    @DisplayName("Flujo feliz: crea el documento en PENDING y dispara la ingesta con los mismos bytes")
    void flujoFeliz() {
        ImportTextUseCase useCase = useCase();

        Document result = useCase.execute("Mi nota", "Contenido de prueba", null);

        ArgumentCaptor<Document> savedDoc = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(savedDoc.capture());
        assertThat(savedDoc.getValue().getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(savedDoc.getValue().getSourceType()).isEqualTo("TEXT");
        assertThat(savedDoc.getValue().getTitle()).isEqualTo("Mi nota");

        ArgumentCaptor<byte[]> ingestedBytes = ArgumentCaptor.forClass(byte[].class);
        verify(ingestionService).processIngestion(eq(result.getId()), ingestedBytes.capture(), eq("Mi nota"));
        assertThat(ingestedBytes.getValue()).isEqualTo("Contenido de prueba".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Debe codificar el contenido en UTF-8 (acentos y ñ intactos)")
    void codificaUtf8() {
        ImportTextUseCase useCase = useCase();
        String contenido = "Café, ñoño, José, año 2026";

        useCase.execute("t", contenido, null);

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(ingestionService).processIngestion(any(), bytes.capture(), any());
        assertThat(bytes.getValue()).isEqualTo(contenido.getBytes(StandardCharsets.UTF_8));
        // Sanity: decodificar de vuelta produce el original
        assertThat(new String(bytes.getValue(), StandardCharsets.UTF_8)).isEqualTo(contenido);
    }

    @Test
    @DisplayName("sourceType no permitido debe lanzar UnsupportedFileTypeException sin persistir ni ingerir")
    void tipoNoPermitidoRechaza() {
        ImportTextUseCase useCase = new ImportTextUseCase(documentRepository, ingestionService);

        assertThatThrownBy(() -> useCase.execute("t", "c", "EXE"))
                .isInstanceOf(UnsupportedFileTypeException.class);

        verifyNoInteractions(ingestionService);
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("sourceType nulo debe usar TEXT por defecto")
    void sourceTypeNuloUsaTextPorDefecto() {
        ImportTextUseCase useCase = useCase();

        Document result = useCase.execute("t", "c", null);

        assertThat(result.getSourceType()).isEqualTo("TEXT");
    }
}
