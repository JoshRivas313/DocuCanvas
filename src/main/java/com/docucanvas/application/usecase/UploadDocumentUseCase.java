package com.docucanvas.application.usecase;

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

    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;

    public UploadDocumentUseCase(DocumentRepository documentRepository,
                                 IngestionService ingestionService) {
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
    }

    public Document execute(MultipartFile file, String title) {
        try {
            byte[] content = file.getBytes();
            String sourceType = getFileExtension(file.getOriginalFilename());
            if (!AllowedFileTypes.isAllowed(sourceType)) {
                throw new UnsupportedFileTypeException(sourceType);
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

            document.attachContent(content);
            Document saved = documentRepository.save(document);

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
