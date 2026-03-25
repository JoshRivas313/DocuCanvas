package com.docucanvas.application.service;

import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentChunk;
import com.docucanvas.domain.model.DocumentStatus;
import com.docucanvas.domain.repository.DocumentRepository;
import com.docucanvas.infrastructure.ai.GeminiEmbeddingAdapter;
import com.docucanvas.infrastructure.ai.SimpleChunker;
import com.docucanvas.infrastructure.storage.TikaExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final DocumentRepository documentRepository;
    private final TikaExtractor tikaExtractor;
    private final SimpleChunker chunker;
    private final GeminiEmbeddingAdapter embeddingAdapter;

    @Async
    public void processIngestion(UUID documentId, MultipartFile file) {
        log.info("Starting ingestion for document: {}", documentId);
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        try {
            document.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(document);

            // 1. Extract Text
            String text = tikaExtractor.extractText(file);
            log.debug("Text extracted ({} characters)", text.length());

            // 2. Chunking
            List<String> textChunks = chunker.splitIntoChunks(text);
            log.debug("Split into {} chunks", textChunks.size());

            // 3. Embedding
            List<float[]> embeddings = embeddingAdapter.embedChunks(textChunks);
            log.debug("Embeddings generated for {} chunks", embeddings.size());

            // 4. Save Chunks
            List<DocumentChunk> domainChunks = IntStream.range(0, textChunks.size())
                    .mapToObj(i -> new DocumentChunk(
                            UUID.randomUUID(),
                            documentId,
                            textChunks.get(i),
                            embeddings.get(i),
                            i,
                            java.time.Instant.now()
                    ))
                    .toList();

            documentRepository.saveChunks(domainChunks);

            // 5. Complete
            document.setStatus(DocumentStatus.READY);
            document.setChunkCount(textChunks.size());
            documentRepository.save(document);
            
            log.info("Ingestion completed successfully for document: {}", documentId);

        } catch (Exception e) {
            log.error("Ingestion failed for document: {}", documentId, e);
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
        }
    }
}
