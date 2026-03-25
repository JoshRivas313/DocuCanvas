package com.docucanvas.infrastructure.persistence.adapter;

import com.docucanvas.domain.model.DocumentChunk;
import com.docucanvas.domain.repository.ChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class JpaChunkRepositoryAdapter implements ChunkRepository {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JpaChunkRepositoryAdapter.class);
    private final JdbcTemplate jdbcTemplate;

    public JpaChunkRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DocumentChunk> findSimilar(float[] embeddingVector, int topK) {
        String pgVector = toArrayLiteral(embeddingVector);
        String sql = """
                SELECT id, document_id, content, chunk_index, created_at
                FROM document_chunks
                ORDER BY embedding <=> '%s'::vector
                LIMIT ?
                """.formatted(pgVector);

        log.debug("Searching for {} similar chunks", topK);

        return jdbcTemplate.query(sql, this::mapChunk, topK);
    }

    private DocumentChunk mapChunk(ResultSet rs, int rowNum) throws SQLException {
        return new DocumentChunk(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("document_id")),
                rs.getString("content"),
                null, // embedding no necesario al recuperar
                rs.getInt("chunk_index"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private String toArrayLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
