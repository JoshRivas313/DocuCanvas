package com.docucanvas.infrastructure.persistence.adapter;

import com.docucanvas.application.chunk.RawChunk;
import com.docucanvas.application.port.out.ChunkReadPort;
import com.docucanvas.application.port.out.ChunkWritePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de persistencia sobre la tabla {@code document_chunks} que gestiona
 * el PGVectorStore de Spring AI. Es el <b>único dueño</b> del acceso por SQL a
 * esa tabla; aísla el detalle de pgvector (parseo del vector, metadata JSON)
 * de la capa de aplicación.
 */
@Component
public class PgVectorChunkAdapter implements ChunkReadPort, ChunkWritePort {

    private static final Logger log = LoggerFactory.getLogger(PgVectorChunkAdapter.class);

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
                .query((rs, rowNum) -> mapRow(rs))
                .list()
                .stream()
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<RawChunk> findByDocument(String documentId) {
        return jdbcClient
                .sql("SELECT id, content, embedding::text AS emb_text, metadata->>'source' AS doc_name " +
                     "FROM document_chunks " +
                     "WHERE metadata->>'documentId' = :documentId " +
                     "ORDER BY (metadata->>'chunkIndex')::int")
                .param("documentId", documentId)
                .query((rs, rowNum) -> mapRow(rs))
                .list()
                .stream()
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Mapea una fila a {@link RawChunk}, o devuelve {@code null} si el embedding
     * es ilegible (el chunk se descartará en lugar de contaminar el PCA con ceros).
     */
    private RawChunk mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        double[] embedding = parseVector(rs.getString("emb_text"));
        if (embedding == null) {
            log.warn("Chunk {} descartado: embedding ilegible o vacío", rs.getString("id"));
            return null;
        }
        return new RawChunk(rs.getString("id"), rs.getString("content"), embedding, rs.getString("doc_name"));
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
     * Devuelve {@code null} si el texto está vacío o es ilegible, para que el
     * chunk se excluya de la visualización en lugar de introducir un vector de
     * ceros que desplazaría el centro de masa del PCA.
     */
    private double[] parseVector(String embText) {
        if (embText == null || embText.length() <= 2) {
            return null;
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
            log.warn("Error parseando vector, se descartará el chunk", e);
            return null;
        }
    }
}
