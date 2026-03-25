package com.docucanvas.infrastructure.persistence.repository;

import com.docucanvas.infrastructure.persistence.entity.JpaDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface JpaDocumentRepository extends JpaRepository<JpaDocumentEntity, UUID> {
}
