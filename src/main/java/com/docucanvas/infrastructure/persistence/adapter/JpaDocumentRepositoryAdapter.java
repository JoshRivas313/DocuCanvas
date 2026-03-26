package com.docucanvas.infrastructure.persistence.adapter;

import com.docucanvas.domain.model.Document;
import com.docucanvas.domain.repository.DocumentRepository;
import com.docucanvas.infrastructure.persistence.entity.JpaDocumentEntity;
import com.docucanvas.infrastructure.persistence.repository.JpaDocumentChunkRepository;
import com.docucanvas.infrastructure.persistence.repository.JpaDocumentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JpaDocumentRepositoryAdapter implements DocumentRepository {

    private final JpaDocumentRepository documentRepository;
    private final JpaDocumentChunkRepository chunkRepository;

    public JpaDocumentRepositoryAdapter(JpaDocumentRepository documentRepository,
                                        JpaDocumentChunkRepository chunkRepository) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

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
        return documentRepository.findAll().stream()
                .skip(offset)
                .limit(limit)
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        // Al borrar el documento, borramos sus chunks de la tabla relacional (si PGVectorStore usa la misma tabla)
        chunkRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
    }

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
}
