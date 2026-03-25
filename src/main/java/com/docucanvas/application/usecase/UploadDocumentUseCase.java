package com.docucanvas.application.usecase;

import com.docucanvas.application.service.IngestionService;
import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentStatus;
import com.docucanvas.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;

    public Document execute(MultipartFile file, String title) {
        Document document = Document.builder()
                .title(title != null ? title : file.getOriginalFilename())
                .sourceType(getFileExtension(file.getOriginalFilename()))
                .status(DocumentStatus.PENDING)
                .build();

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
