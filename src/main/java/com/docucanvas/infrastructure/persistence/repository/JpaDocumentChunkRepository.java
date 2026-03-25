package com.docucanvas.infrastructure.persistence.repository;

import com.docucanvas.infrastructure.persistence.entity.JpaDocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JpaDocumentChunkRepository extends JpaRepository<JpaDocumentChunkEntity, UUID> {
    List<JpaDocumentChunkEntity> findByDocumentId(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
