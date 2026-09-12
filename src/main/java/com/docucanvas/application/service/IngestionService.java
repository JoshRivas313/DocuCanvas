package com.docucanvas.application.service;

import com.docucanvas.domain.exception.DocumentNotFoundException;
import com.docucanvas.domain.repository.DocumentRepository;
import com.docucanvas.infrastructure.config.RagProperties;
import com.docucanvas.infrastructure.storage.TikaExtractor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
            VectorStore vectorStore,
            RagProperties ragProperties) {
        this.documentRepository = documentRepository;
        this.tikaExtractor = tikaExtractor;
        this.vectorStore = vectorStore;
        // Parámetros de chunking externalizados a docucanvas.rag.chunking (ver
        // RagProperties.Chunking para el porqué de cada uno). Los defaults
        // reproducen exactamente los valores que antes estaban incrustados aquí.
        RagProperties.Chunking chunking = ragProperties.chunking();
        this.tokenTextSplitter = new TokenTextSplitter(
                chunking.chunkSize(),
                chunking.minChunkSizeChars(),
                chunking.minChunkLengthToEmbed(),
                chunking.maxNumChunks(),
                chunking.keepSeparator());
        log.info("Chunking configurado: chunkSize={} tokens, minChunkSizeChars={}, maxNumChunks={}",
                chunking.chunkSize(), chunking.minChunkSizeChars(), chunking.maxNumChunks());
    }

    @Async
    @CacheEvict(value = { "chunksGlobal", "chunksDoc" }, allEntries = true)
    public void processIngestion(UUID documentId, byte[] fileContent, String originalFilename) {
        log.info("[INGESTION] Documento recibido: {} ({})", originalFilename, documentId);

        com.docucanvas.domain.model.Document domainDocument = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        try {
            // El binario ya se persistió al crear el documento; aquí solo se
            // actualiza el estado (no se reescribe el contenido).
            domainDocument.markProcessing();
            documentRepository.save(domainDocument);

            // 1. Extraer texto con tracking de página (real para PDF; el resto
            // de formatos se trata como una única "página").
            List<String> pages = tikaExtractor.extractPages(
                    fileContent, originalFilename, domainDocument.getSourceType());
            log.info("[INGESTION] Texto extraido: {} pagina(s)", pages.size());

            // 2. Chunking por página: cada chunk conserva la página de origen
            // en su metadata, necesaria para citar la evidencia con precisión
            // en el pipeline RAG.
            List<Document> springAiDocs = new ArrayList<>();
            int chunkIndex = 0;
            for (int pageIdx = 0; pageIdx < pages.size(); pageIdx++) {
                String pageText = pages.get(pageIdx);
                if (pageText.isBlank()) continue;

                List<Document> pageChunks = tokenTextSplitter.apply(List.of(new Document(pageText)));
                for (Document chunk : pageChunks) {
                    chunk.getMetadata().putAll(Map.of(
                            "documentId", documentId.toString(),
                            "chunkIndex", chunkIndex++,
                            "source", originalFilename,
                            "page", pageIdx + 1));
                    springAiDocs.add(chunk);
                }
            }
            log.info("[CHUNKING] {} chunks generados", springAiDocs.size());

            // Cero chunks = no hay nada que indexar, y marcarlo READY seria
            // mentir: el documento aparece "listo" en la lista y luego ninguna
            // pregunta encuentra nada. El caso tipico es un PDF escaneado, sin
            // capa de texto, del que PDFBox no extrae ni un caracter.
            if (springAiDocs.isEmpty()) {
                log.warn("[INGESTION] '{}' no produjo ningun chunk: el archivo no tiene texto "
                        + "extraible (¿PDF escaneado sin OCR?). Se marca FAILED.", originalFilename);
                domainDocument.markFailed();
                documentRepository.save(domainDocument);
                return;
            }

            // 3. Ingesta Vectorial (Embedding + Save en un solo paso)
            // Spring AI se encarga de llamar al EmbeddingModel configurado en YAML
            log.info("[EMBEDDING] Generando embeddings de {} chunks", springAiDocs.size());
            long embStart = System.currentTimeMillis();
            vectorStore.add(springAiDocs);
            log.info("[VECTOR STORE] {} chunks persistidos en {} ms",
                    springAiDocs.size(), System.currentTimeMillis() - embStart);

            // 4. Completar Proceso
            domainDocument.markReady(springAiDocs.size());
            documentRepository.save(domainDocument);

            log.info("[INGESTION] Completada: {}", originalFilename);

        } catch (Exception e) {
            log.error("Fallo en la ingesta para documento: {}", documentId, e);
            domainDocument.markFailed();
            documentRepository.save(domainDocument);
        }
    }
}
