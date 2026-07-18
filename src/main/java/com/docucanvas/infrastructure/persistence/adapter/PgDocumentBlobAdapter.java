package com.docucanvas.infrastructure.persistence.adapter;

import com.docucanvas.application.port.out.BlobStoragePort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de persistencia para {@link BlobStoragePort} sobre la tabla
 * {@code document_blobs}. Único dueño del acceso SQL a esa tabla, igual que
 * {@link PgVectorChunkAdapter} lo es de {@code document_chunks}.
 */
@Component
public class PgDocumentBlobAdapter implements BlobStoragePort {

    private final JdbcClient jdbcClient;

    public PgDocumentBlobAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void store(UUID documentId, byte[] content) {
        jdbcClient
                .sql("INSERT INTO document_blobs (document_id, content) VALUES (:documentId, :content) " +
                     "ON CONFLICT (document_id) DO UPDATE SET content = EXCLUDED.content")
                .param("documentId", documentId)
                .param("content", content)
                .update();
    }

    @Override
    public Optional<byte[]> find(UUID documentId) {
        return jdbcClient
                .sql("SELECT content FROM document_blobs WHERE document_id = :documentId")
                .param("documentId", documentId)
                .query((rs, rowNum) -> rs.getBytes("content"))
                .optional();
    }
}
