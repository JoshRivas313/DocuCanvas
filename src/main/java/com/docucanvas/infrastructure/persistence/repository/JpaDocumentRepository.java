package com.docucanvas.infrastructure.persistence.repository;

import com.docucanvas.infrastructure.persistence.entity.JpaDocumentEntity;
import com.docucanvas.infrastructure.persistence.projection.DocumentSummaryView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaDocumentRepository extends JpaRepository<JpaDocumentEntity, UUID> {

    /**
     * Listado paginado sin el binario del archivo. La proyección
     * {@link DocumentSummaryView} hace que la consulta excluya {@code file_content}.
     */
    List<DocumentSummaryView> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
