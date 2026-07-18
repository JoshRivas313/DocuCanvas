package com.docucanvas.application.usecase;

import com.docucanvas.application.port.out.BlobStoragePort;
import com.docucanvas.application.service.IngestionService;
import com.docucanvas.domain.exception.UnsupportedFileTypeException;
import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentStatus;
import com.docucanvas.domain.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ingesta de texto pegado directamente (sin archivo). Simétrico a
 * {@link UploadDocumentUseCase}: construye el agregado vía el dominio,
 * persiste y dispara la ingesta asíncrona, en vez de hacerlo desde el
 * controller como ocurría antes.
 */
@Service
public class ImportTextUseCase {

    private static final String DEFAULT_SOURCE_TYPE = "TEXT";

    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;
    private final BlobStoragePort blobStoragePort;

    public ImportTextUseCase(DocumentRepository documentRepository,
                             IngestionService ingestionService,
                             BlobStoragePort blobStoragePort) {
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
        this.blobStoragePort = blobStoragePort;
    }

    public Document execute(String title, String content, String requestedSourceType) {
        String sourceType = requestedSourceType != null ? requestedSourceType.toUpperCase() : DEFAULT_SOURCE_TYPE;
        if (!AllowedFileTypes.isAllowed(sourceType)) {
            throw new UnsupportedFileTypeException(sourceType);
        }

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        Document document = Document.builder()
                .id(UUID.randomUUID())
                .title(title)
                .sourceType(sourceType)
                .status(DocumentStatus.PENDING)
                .chunkCount(0)
                .tags(List.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Document saved = documentRepository.save(document);
        blobStoragePort.store(saved.getId(), bytes);

        ingestionService.processIngestion(saved.getId(), bytes, title);

        return saved;
    }
}
