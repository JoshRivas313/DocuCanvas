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
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Tests unitarios de {@link UploadDocumentUseCase}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("UploadDocumentUseCase — Ingesta de archivo")
class UploadDocumentUseCaseTest {

    @Mock private com.docucanvas.domain.repository.DocumentRepository documentRepository;
    @Mock private IngestionService ingestionService;

    private UploadDocumentUseCase useCase() {
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        return new UploadDocumentUseCase(documentRepository, ingestionService);
    }

    @Test
    @DisplayName("Flujo feliz: deriva el tipo de la extensión, crea en PENDING y dispara ingesta con los bytes")
    void flujoFeliz() {
        UploadDocumentUseCase useCase = useCase();
        byte[] contenido = "%PDF-1.4 contenido".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "informe.pdf", "application/pdf", contenido);

        Document result = useCase.execute(file, "Informe anual");

        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(saved.getValue().getSourceType()).isEqualTo("PDF");
        assertThat(saved.getValue().getFileContent()).isEqualTo(contenido);

        verify(ingestionService).processIngestion(eq(result.getId()), eq(contenido), eq("informe.pdf"));
    }

    @Test
    @DisplayName("Si no se pasa título usa el nombre original del archivo")
    void sinTituloUsaNombreOriginal() {
        UploadDocumentUseCase useCase = useCase();
        MockMultipartFile file = new MockMultipartFile("file", "notas.txt", "text/plain", "hola".getBytes());

        Document result = useCase.execute(file, null);

        assertThat(result.getTitle()).isEqualTo("notas.txt");
    }

    @Test
    @DisplayName("Extensión no permitida debe lanzar UnsupportedFileTypeException sin persistir ni ingerir")
    void extensionNoPermitidaRechaza() {
        UploadDocumentUseCase useCase = new UploadDocumentUseCase(documentRepository, ingestionService);
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "MZ".getBytes());

        assertThatThrownBy(() -> useCase.execute(file, "x"))
                .isInstanceOf(UnsupportedFileTypeException.class);

        verifyNoInteractions(ingestionService);
        verify(documentRepository, never()).save(any());
    }
}
