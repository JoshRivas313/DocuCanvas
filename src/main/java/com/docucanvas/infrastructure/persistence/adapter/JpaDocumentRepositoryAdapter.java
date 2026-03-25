package com.docucanvas.infrastructure.persistence.adapter;

import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.model.DocumentChunk;
import com.docucanvas.domain.repository.DocumentRepository;
import com.docucanvas.infrastructure.persistence.entity.JpaDocumentChunkEntity;
import com.docucanvas.infrastructure.persistence.entity.JpaDocumentEntity;
import com.docucanvas.infrastructure.persistence.repository.JpaDocumentChunkRepository;
import com.docucanvas.infrastructure.persistence.repository.JpaDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JpaDocumentRepositoryAdapter implements DocumentRepository {

    private final JpaDocumentRepository documentRepository;
    private final JpaDocumentChunkRepository chunkRepository;

    @Override
    public Document save(Document document) {
        JpaDocumentEntity entity = toEntity(document);
        JpaDocumentEntity saved = documentRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return documentRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Document> findAll(int limit, int offset) {
        // Simple implementation for MVP, in production use Pageable
        return documentRepository.findAll().stream()
                .skip(offset)
                .limit(limit)
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        chunkRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void saveChunks(List<DocumentChunk> chunks) {
        List<JpaDocumentChunkEntity> entities = chunks.stream()
                .map(this::toChunkEntity)
                .collect(Collectors.toList());
        chunkRepository.saveAll(entities);
    }

    @Override
    public List<DocumentChunk> findChunksByDocumentId(UUID documentId) {
        return chunkRepository.findByDocumentId(documentId).stream()
                .map(this::toChunkDomain)
                .collect(Collectors.toList());
    }

    // Mapping Methods (Private for now, could be moved to a Mapper class)
    private JpaDocumentEntity toEntity(Document d) {
        return JpaDocumentEntity.builder()
                .id(d.getId())
                .title(d.getTitle())
                .sourceType(d.getSourceType())
                .status(d.getStatus())
                .chunkCount(d.getChunkCount())
                .tags(d.getTags())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    private Document toDomain(JpaDocumentEntity e) {
        return Document.builder()
                .id(e.getId())
                .title(e.getTitle())
                .sourceType(e.getSourceType())
                .status(e.getStatus())
                .chunkCount(e.getChunkCount())
                .tags(e.getTags())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private JpaDocumentChunkEntity toChunkEntity(DocumentChunk c) {
        return JpaDocumentChunkEntity.builder()
                .id(c.id())
                .documentId(c.documentId())
                .content(c.content())
                .embedding(c.embedding())
                .chunkIndex(c.chunkIndex())
                .createdAt(c.createdAt())
                .build();
    }

    private DocumentChunk toChunkDomain(JpaDocumentChunkEntity e) {
        return new DocumentChunk(
                e.getId(),
                e.getDocumentId(),
                e.getContent(),
                e.getEmbedding(),
                e.getChunkIndex(),
                e.getCreatedAt()
        );
    }
}
