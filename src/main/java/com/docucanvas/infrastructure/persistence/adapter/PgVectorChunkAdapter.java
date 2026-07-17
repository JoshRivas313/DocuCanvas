package com.docucanvas.infrastructure.persistence.adapter;

import com.docucanvas.application.chunk.RawChunk;
import com.docucanvas.application.port.out.ChunkReadPort;
import com.docucanvas.application.port.out.ChunkWritePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.List;

/**
 * Adaptador de persistencia sobre la tabla {@code document_chunks} que gestiona
 * el PGVectorStore de Spring AI. Es el <b>único dueño</b> del acceso por SQL a
 * esa tabla; aísla el detalle de pgvector (parseo del vector, metadata JSON)
 * de la capa de aplicación.
 */
@Component
public class PgVectorChunkAdapter implements ChunkReadPort, ChunkWritePort {

    private static final Logger log = LoggerFactory.getLogger(PgVectorChunkAdapter.class);

    /** Dimensión del embedding (nomic-embed-text de Ollama). */
    private static final int EMBEDDING_DIMENSIONS = 768;

    private final JdbcClient jdbcClient;

    public PgVectorChunkAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<RawChunk> findForGlobalView(int limit) {
        return jdbcClient
                .sql("SELECT id, content, embedding::text AS emb_text, metadata->>'source' AS doc_name " +
                     "FROM document_chunks " +
                     "ORDER BY metadata->>'documentId', (metadata->>'chunkIndex')::int " +
                     "LIMIT :limit")
                .param("limit", limit)
                .query((rs, rowNum) -> new RawChunk(
                        rs.getString("id"),
                        rs.getString("content"),
                        parseVector(rs.getString("emb_text")),
                        rs.getString("doc_name")))
                .list();
    }

    @Override
    public List<RawChunk> findByDocument(String documentId) {
        return jdbcClient
                .sql("SELECT id, content, embedding::text AS emb_text, metadata->>'source' AS doc_name " +
                     "FROM document_chunks " +
                     "WHERE metadata->>'documentId' = :documentId " +
                     "ORDER BY (metadata->>'chunkIndex')::int")
                .param("documentId", documentId)
                .query((rs, rowNum) -> new RawChunk(
                        rs.getString("id"),
                        rs.getString("content"),
                        parseVector(rs.getString("emb_text")),
                        rs.getString("doc_name")))
                .list();
    }

    @Override
    public void deleteByDocument(UUID documentId) {
        int deleted = jdbcClient
                .sql("DELETE FROM document_chunks WHERE metadata->>'documentId' = :documentId")
                .param("documentId", documentId.toString())
                .update();
        log.info("Eliminados {} chunks del documento {}", deleted, documentId);
    }

    /**
     * Parsea el vector en formato textual de pgvector ({@code [0.1,0.2,...]}).
     * Ante un vector ilegible devuelve un vector de ceros para no romper el
     * pipeline de visualización (comportamiento preservado de la versión previa).
     */
    private double[] parseVector(String embText) {
        if (embText == null || embText.length() <= 2) {
            return new double[EMBEDDING_DIMENSIONS];
        }
        try {
            String clean = embText.substring(1, embText.length() - 1);
            String[] parts = clean.split(",");
            double[] vector = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Double.parseDouble(parts[i].trim());
            }
            return vector;
        } catch (Exception e) {
            log.warn("Error parseando vector, usando vector nulo", e);
            return new double[EMBEDDING_DIMENSIONS];
        }
    }
}
