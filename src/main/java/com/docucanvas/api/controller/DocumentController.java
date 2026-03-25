package com.docucanvas.api.controller;

import com.docucanvas.api.dto.response.AcceptedJobResponse;
import com.docucanvas.api.dto.response.IngestionJobResponse;
import com.docucanvas.application.usecase.UploadDocumentUseCase;
import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentController {

    private final UploadDocumentUseCase uploadDocumentUseCase;
    private final DocumentRepository documentRepository;

    @PostMapping("/documents/upload")
    public ResponseEntity<AcceptedJobResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {

        Document doc = uploadDocumentUseCase.execute(file, title);

        AcceptedJobResponse response = new AcceptedJobResponse(
                doc.getId(),
                doc.getId(),
                doc.getStatus(),
                "/api/v1/ingestion-jobs/" + doc.getId()
        );

        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/ingestion-jobs/{jobId}")
    public ResponseEntity<IngestionJobResponse> getJobStatus(@PathVariable UUID jobId) {
        return documentRepository.findById(jobId)
                .map(doc -> new IngestionJobResponse(
                        doc.getId(),
                        doc.getId(),
                        doc.getStatus(),
                        doc.getChunkCount(),
                        null,
                        doc.getCreatedAt(),
                        doc.getUpdatedAt()
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

