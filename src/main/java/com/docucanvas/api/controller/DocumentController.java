package com.docucanvas.api.controller;

import com.docucanvas.api.dto.request.TextImportRequest;
import com.docucanvas.api.dto.response.AcceptedJobResponse;
import com.docucanvas.api.dto.response.IngestionJobResponse;
import com.docucanvas.application.service.IngestionService;
import com.docucanvas.application.usecase.ImportTextUseCase;
import com.docucanvas.application.usecase.UploadDocumentUseCase;
import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentSummary;
import com.docucanvas.domain.repository.DocumentRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final UploadDocumentUseCase uploadDocumentUseCase;
    private final ImportTextUseCase importTextUseCase;
    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;

    public DocumentController(UploadDocumentUseCase uploadDocumentUseCase,
                              ImportTextUseCase importTextUseCase,
                              DocumentRepository documentRepository,
                              IngestionService ingestionService) {
        this.uploadDocumentUseCase = uploadDocumentUseCase;
        this.importTextUseCase = importTextUseCase;
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/documents")
    public List<DocumentSummary> getAllDocuments() {
        return documentRepository.findSummaries(100, 0);
    }

    @PostMapping("/documents/import-text")
    public ResponseEntity<DocumentSummary> importText(@Valid @RequestBody TextImportRequest request) {
        Document saved = importTextUseCase.execute(request.title(), request.content(), request.sourceType());
        return ResponseEntity.ok(toSummary(saved));
    }

    private DocumentSummary toSummary(Document d) {
        return new DocumentSummary(
                d.getId(), d.getTitle(), d.getSourceType(), d.getStatus(),
                d.getChunkCount(), d.getTags(), d.getCreatedAt(), d.getUpdatedAt());
    }

    @PostMapping("/documents/upload")
    public ResponseEntity<AcceptedJobResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {

        Document doc = uploadDocumentUseCase.execute(file, title);
        AcceptedJobResponse response = new AcceptedJobResponse(
                doc.getId(), doc.getId(), doc.getStatus(), "/api/v1/ingestion-jobs/" + doc.getId());
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/ingestion-jobs/{jobId}")
    public ResponseEntity<IngestionJobResponse> getJobStatus(@PathVariable UUID jobId) {
        return documentRepository.findById(jobId)
                .map(doc -> new IngestionJobResponse(
                        doc.getId(), doc.getId(), doc.getStatus(), doc.getChunkCount(), 
                        null, doc.getCreatedAt(), doc.getUpdatedAt()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<byte[]> getDocumentFile(@PathVariable UUID id) {
        return documentRepository.findById(id)
                .filter(doc -> doc.getFileContent() != null)
                .map(doc -> {
                    String contentType = switch (doc.getSourceType().toUpperCase()) {
                        case "PDF" -> "application/pdf";
                        case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                        case "TXT", "TEXT" -> "text/plain";
                        case "PNG" -> "image/png";
                        case "JPG", "JPEG" -> "image/jpeg";
                        case "JSON" -> "application/json";
                        default -> "application/octet-stream";
                    };
                    
                    return ResponseEntity.ok()
                            .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, contentType)
                            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getTitle() + "\"")
                            .header("X-Frame-Options", "SAMEORIGIN")
                            .header("Content-Security-Policy", "frame-ancestors 'self'")
                            .body(doc.getFileContent());
                })
                .orElse(ResponseEntity.notFound().build());
    }
}


