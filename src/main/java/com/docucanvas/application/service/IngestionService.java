package com.docucanvas.application.service;

import com.docucanvas.domain.model.DocumentStatus;
import com.docucanvas.domain.repository.DocumentRepository;
import com.docucanvas.infrastructure.storage.TikaExtractor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IngestionService.class);
    private final DocumentRepository documentRepository;
    private final TikaExtractor tikaExtractor;
    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenTextSplitter;

    public IngestionService(DocumentRepository documentRepository,
                            TikaExtractor tikaExtractor,
                            VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.tikaExtractor = tikaExtractor;
        this.vectorStore = vectorStore;
        // Chunking optimizado para demostración visual y ahorro de costos en el LLM
        this.tokenTextSplitter = new TokenTextSplitter(200, 50, 5, 10000, true);
    }

    @Async
    public void processIngestion(UUID documentId, byte[] fileContent, String originalFilename) {
        log.info("Starting ingestion for document: {}", documentId);
        
        com.docucanvas.domain.model.Document domainDocument = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        try {
            domainDocument.setStatus(DocumentStatus.PROCESSING);
            domainDocument.setFileContent(fileContent);
            documentRepository.save(domainDocument);

            // 1. Extraer Texto con Tika
            String text = tikaExtractor.extractText(fileContent, originalFilename);
            log.debug("Texto extraído ({} caracteres)", text.length());

            // 2. Chunking Inteligente con Spring AI
            List<Document> springAiDocs = tokenTextSplitter.apply(List.of(new Document(text)));
            
            // Enriquecer con Metadatos (Pilar del RAG)
            for (int i = 0; i < springAiDocs.size(); i++) {
                Document doc = springAiDocs.get(i);
                doc.getMetadata().putAll(Map.of(
                        "documentId", documentId.toString(),
                        "chunkIndex", i,
                        "source", originalFilename
                ));
            }
            log.debug("Dividido en {} chunks inteligentes", springAiDocs.size());

            // 3. Ingesta Vectorial (Embedding + Save en un solo paso)
            // Spring AI se encarga de llamar al EmbeddingModel configurado en YAML
            vectorStore.add(springAiDocs);
            log.info("Vectores persistidos en VectorStore para el documento: {}", documentId);

            // 5. Completar Proceso
            domainDocument.setStatus(DocumentStatus.READY);
            domainDocument.setChunkCount(springAiDocs.size());
            documentRepository.save(domainDocument);
            
            log.info("Ingesta completada exitosamente para: {}", originalFilename);

        } catch (Exception e) {
            log.error("Fallo en la ingesta para documento: {}", documentId, e);
            domainDocument.setStatus(DocumentStatus.FAILED);
            documentRepository.save(domainDocument);
        }
    }
}
