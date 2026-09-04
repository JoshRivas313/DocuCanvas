package com.docucanvas.application.usecase;

import com.docucanvas.application.port.out.BlobStoragePort;
import com.docucanvas.application.port.out.FileTypeDetectorPort;
import com.docucanvas.application.service.IngestionService;
import com.docucanvas.domain.exception.UnsupportedFileTypeException;
import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentStatus;
import com.docucanvas.domain.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UploadDocumentUseCase {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(UploadDocumentUseCase.class);

    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;
    private final BlobStoragePort blobStoragePort;
    private final FileTypeDetectorPort fileTypeDetector;

    public UploadDocumentUseCase(DocumentRepository documentRepository,
                                 IngestionService ingestionService,
                                 BlobStoragePort blobStoragePort,
                                 FileTypeDetectorPort fileTypeDetector) {
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
        this.blobStoragePort = blobStoragePort;
        this.fileTypeDetector = fileTypeDetector;
    }

    public Document execute(MultipartFile file, String title) {
        try {
            byte[] content = file.getBytes();
            String sourceType = getFileExtension(file.getOriginalFilename());

            // 1. La extensión declarada debe estar en la política de tipos.
            if (!AllowedFileTypes.isAllowed(sourceType)) {
                throw new UnsupportedFileTypeException(sourceType);
            }

            // 2. Y el contenido real debe corresponderse con ella. El nombre lo
            // controla el cliente; los magic bytes, no. Sin este paso, un
            // ejecutable renombrado a .pdf llegaba intacto a PDFBox.
            String detected = fileTypeDetector.detectMediaType(content, file.getOriginalFilename());
            if (!AllowedFileTypes.matchesDetectedType(sourceType, detected)) {
                log.warn("Subida rechazada: '{}' declara .{} pero su contenido es {}",
                        file.getOriginalFilename(), sourceType.toLowerCase(), detected);
                throw UnsupportedFileTypeException.contentMismatch(sourceType, detected);
            }

            Document document = Document.builder()
                    .id(UUID.randomUUID())
                    .title(title != null ? title : file.getOriginalFilename())
                    .sourceType(sourceType)
                    .status(DocumentStatus.PENDING)
                    .chunkCount(0)
                    .tags(List.of())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            Document saved = documentRepository.save(document);
            blobStoragePort.store(saved.getId(), content);

            // Trigger async processing (reutiliza el mismo array, sin releer el archivo)
            ingestionService.processIngestion(saved.getId(), content, file.getOriginalFilename());

            return saved;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not read file", e);
        }
    }


    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "UNKNOWN";
        return filename.substring(filename.lastIndexOf(".") + 1).toUpperCase();
    }
}
