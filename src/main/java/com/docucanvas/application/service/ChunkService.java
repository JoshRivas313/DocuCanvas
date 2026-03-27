package com.docucanvas.application.service;

import com.docucanvas.api.dto.ChunkDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de acceso a los chunks vectorizados almacenados en PGVector.
 *
 * <p>Centraliza las consultas de visualización e inspección del VectorStore,
 * respetando la separación de capas y facilitando el testing.
 */
@Service
public class ChunkService {

    private static final Logger log = LoggerFactory.getLogger(ChunkService.class);
    private static final int MAX_CONTENT_PREVIEW = 100;
    private static final int MAX_CHUNKS_VISUALIZE = 300;

    private final JdbcClient jdbcClient;

    public ChunkService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Recupera hasta {@value #MAX_CHUNKS_VISUALIZE} chunks con sus coordenadas
     * reducidas (primeras 3 dimensiones del vector) para visualización 3D.
     *
     * @return lista de DTOs listos para el frontend de visualización
     */
    public List<ChunkDTO> getChunksForVisualization() {
        log.debug("Consultando chunks para visualización 3D");

        return jdbcClient
                .sql("""
                        SELECT id,
                               content,
                               embedding::text AS emb_text,
                               metadata->>'file_name' AS doc_name
                        FROM document_chunks
                        LIMIT :limit
                        """)
                .param("limit", MAX_CHUNKS_VISUALIZE)
                .query((rs, rowNum) -> {
                    String id = rs.getString("id");

                    String content = rs.getString("content");
                    if (content != null && content.length() > MAX_CONTENT_PREVIEW) {
                        content = content.substring(0, MAX_CONTENT_PREVIEW) + "...";
                    }

                    String docName = rs.getString("doc_name");
                    if (docName == null) docName = "Desconocido";

                    List<Double> coords = parseFirstThreeDimensions(rs.getString("emb_text"));
                    return new ChunkDTO(id, content, coords, docName);
                })
                .list();
    }

    /**
     * Recupera todos los chunks asociados a un documento específico.
     * Utilizado para la vista de "Indexación" aislada.
     *
     * @param documentId identificador del documento
     * @return lista de DTOs con el contenido completo de los chunks
     */
    public List<ChunkDTO> getChunksByDocumentId(String documentId) {
        log.debug("Consultando chunks para el documento: {}", documentId);

        return jdbcClient
                .sql("""
                        SELECT id,
                               content,
                               embedding::text AS emb_text,
                               metadata->>'file_name' AS doc_name
                        FROM document_chunks
                        WHERE metadata->>'documentId' = :documentId
                        ORDER BY metadata->>'chunk_index'
                        """)
                .param("documentId", documentId)
                .query((rs, rowNum) -> {
                    String id = rs.getString("id");
                    String content = rs.getString("content");
                    
                    String docName = rs.getString("doc_name");
                    if (docName == null) docName = "Desconocido";

                    List<Double> coords = parseFirstThreeDimensions(rs.getString("emb_text"));
                    return new ChunkDTO(id, content, coords, docName);
                })
                .list();
    }

    /**
     * Extrae las primeras 3 dimensiones de un vector PGVector (formato "[d1,d2,d3,...]")
     * para proyección en espacio 3D.
     */
    private List<Double> parseFirstThreeDimensions(String embText) {
        if (embText == null || embText.length() <= 2) {
            return List.of(0.0, 0.0, 0.0);
        }
        try {
            String clean = embText.substring(1, embText.length() - 1);
            return Arrays.stream(clean.split(","))
                    .limit(3)
                    .map(String::trim)
                    .map(Double::parseDouble)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("No se pudo parsear el vector de embedding, usando coordenadas por defecto", e);
            return List.of(0.0, 0.0, 0.0);
        }
    }
}
