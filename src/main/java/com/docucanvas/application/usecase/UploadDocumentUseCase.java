package com.docucanvas.application.usecase;

import com.docucanvas.application.service.IngestionService;
import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentStatus;
import com.docucanvas.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
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
        String sourceType = getFileExtension(file.getOriginalFilename());
        Document document = new Document(
                UUID.randomUUID(),
                title != null ? title : file.getOriginalFilename(),
                sourceType,
                DocumentStatus.PENDING,
                0,
                List.of(),
                Instant.now(),
                Instant.now()
        );

        Document saved = documentRepository.save(document);
        
        // Trigger async processing
        ingestionService.processIngestion(saved.getId(), file);

        return saved;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "UNKNOWN";
        return filename.substring(filename.lastIndexOf(".") + 1).toUpperCase();
    }
}
