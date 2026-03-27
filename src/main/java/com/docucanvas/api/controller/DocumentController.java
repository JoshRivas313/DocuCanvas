package com.docucanvas.api.controller;

import com.docucanvas.api.dto.request.TextImportRequest;
import com.docucanvas.api.dto.response.AcceptedJobResponse;
import com.docucanvas.api.dto.response.IngestionJobResponse;
import com.docucanvas.application.service.IngestionService;
import com.docucanvas.application.usecase.UploadDocumentUseCase;
import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentStatus;
import com.docucanvas.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final UploadDocumentUseCase uploadDocumentUseCase;
    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;

    public DocumentController(UploadDocumentUseCase uploadDocumentUseCase, 
                              DocumentRepository documentRepository, 
                              IngestionService ingestionService) {
        this.uploadDocumentUseCase = uploadDocumentUseCase;
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
    }

    @GetMapping("/documents")
    public List<Document> getAllDocuments() {
        return documentRepository.findAll(100, 0);
    }

    @PostMapping("/documents/import-text")
    public ResponseEntity<Document> importText(@RequestBody TextImportRequest request) {
        Document doc = Document.builder()
                .id(UUID.randomUUID())
                .title(request.title())
                .sourceType(request.sourceType() != null ? request.sourceType() : "TEXT")
                .status(DocumentStatus.PENDING)
                .chunkCount(0)
                .tags(List.of())
                .fileContent(request.content().getBytes())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        Document saved = documentRepository.save(doc);
        ingestionService.processIngestion(saved.getId(), request.content().getBytes(), "text.txt");
        
        return ResponseEntity.ok(saved);
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
                        default -> "application/octet-stream";
                    };
                    
                    return ResponseEntity.ok()
                            .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, contentType)
                            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getTitle() + "\"")
                            .body(doc.getFileContent());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Inner class to avoid external test dependencies
    private static class SimpleMultipartFile implements MultipartFile {
        private final String name;
        private final String contentType;
        private final byte[] content;
        public SimpleMultipartFile(String name, String contentType, byte[] content) {
            this.name = name; this.contentType = contentType; this.content = content;
        }
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) { throw new UnsupportedOperationException(); }
    }
}


