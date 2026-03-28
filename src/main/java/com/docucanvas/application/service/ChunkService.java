package com.docucanvas.application.service;

import com.docucanvas.api.dto.ChunkDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de acceso a los chunks vectorizados almacenados en PGVector.
 *
 * <p>Centraliza las consultas de visualización e inspección del VectorStore,
 * integrando PCA para reducción de 1536d a 3d y Clustering dinámico (KMeans).
 */
@Service
public class ChunkService {

    private static final Logger log = LoggerFactory.getLogger(ChunkService.class);
    private static final int MAX_CONTENT_PREVIEW = 100;
    private static final int MAX_CHUNKS_VISUALIZE = 300;

    private final JdbcClient jdbcClient;
    private final PcaService pcaService;
    private final ClusteringService clusteringService;

    public ChunkService(JdbcClient jdbcClient, PcaService pcaService, ClusteringService clusteringService) {
        this.jdbcClient = jdbcClient;
        this.pcaService = pcaService;
        this.clusteringService = clusteringService;
    }

    /**
     * Recupera chunks y aplica PCA + Clustering dinámico para visualización.
     */
    @Cacheable(value = "chunksGlobal")
    public List<ChunkDTO> getChunksForVisualization() {
        log.info("Calculando proyección 3D con PCA y Clustering dinámico para vista global (CACHE MISS)");

        List<RawChunkData> rawData = jdbcClient
                .sql("SELECT id, content, embedding::text as emb_text, metadata->>'source' as doc_name FROM document_chunks LIMIT :limit")
                .param("limit", MAX_CHUNKS_VISUALIZE)
                .query((rs, rowNum) -> new RawChunkData(
                        rs.getString("id"),
                        rs.getString("content"),
                        parseFullVector(rs.getString("emb_text")),
                        rs.getString("doc_name")
                ))
                .list();

        if (rawData.isEmpty()) return List.of();

        return processAndCluster(rawData);
    }

    /**
     * Recupera todos los chunks asociados a un documento con su proyección PCA.
     */
    @Cacheable(value = "chunksDoc", key = "#documentId")
    public List<ChunkDTO> getChunksByDocumentId(String documentId) {
        log.info("Generando vista de indexación para documento: {} (CACHE MISS)", documentId);

        List<RawChunkData> rawData = jdbcClient
                .sql("SELECT id, content, embedding::text as emb_text, metadata->>'source' as doc_name FROM document_chunks " +
                     "WHERE metadata->>'documentId' = :documentId ORDER BY (metadata->>'chunkIndex')::int")
                .param("documentId", documentId)
                .query((rs, rowNum) -> new RawChunkData(
                        rs.getString("id"),
                        rs.getString("content"),
                        parseFullVector(rs.getString("emb_text")),
                        rs.getString("doc_name")
                ))
                .list();

        if (rawData.isEmpty()) return List.of();

        return processAndCluster(rawData);
    }

    private List<ChunkDTO> processAndCluster(List<RawChunkData> rawData) {
        // 1. Reducción de Dimensiones (PCA: 1536 -> 3)
        double[][] matrix = pcaService.convertToMatrix(rawData.stream().map(r -> r.vector).toList());
        double[][] projected = pcaService.projectTo3D(matrix);

        // 2. Clustering (KMeans++ con k dinámico)
        // Escalado más agresivo para que en demos pequeñas (3-10 chunks) se vean los grupos (k=3+)
        int k = Math.min(10, Math.max(2, (int) Math.ceil(rawData.size() / 5.0)));
        if (rawData.size() <= 3) k = rawData.size(); // Si hay 3 chunks de 3 temas, queremos 3 grupos
        
        int[] clusterAssignments = clusteringService.cluster(projected, k);

        // 3. Agrupar contenidos por cluster para nombres descriptivos
        Map<Integer, List<String>> clusterContents = new HashMap<>();
        for (int i = 0; i < rawData.size(); i++) {
            clusterContents.computeIfAbsent(clusterAssignments[i], v -> new ArrayList<>()).add(rawData.get(i).content);
        }
        
        Map<Integer, ClusteringService.ClusterMetadata> clusterMeta = new HashMap<>();
        for (int i = 0; i < k; i++) {
            clusterMeta.put(i, clusteringService.generateClusterMetadata(i, clusterContents.getOrDefault(i, List.of())));
        }

        // 4. Construir DTOs
        List<ChunkDTO> result = new ArrayList<>();
        for (int i = 0; i < rawData.size(); i++) {
            RawChunkData raw = rawData.get(i);
            int clusterIdx = clusterAssignments[i];
            ClusteringService.ClusterMetadata meta = clusterMeta.get(clusterIdx);
            
            result.add(new ChunkDTO(
                    raw.id,
                    truncateContent(raw.content),
                    Arrays.stream(projected[i]).boxed().toList(),
                    raw.docName != null ? raw.docName : "Desconocido",
                    meta.name(),
                    meta.description(),
                    clusteringService.getColor(clusterIdx)
            ));
        }
        return result;
    }

    private List<Double> parseFullVector(String embText) {
        if (embText == null || embText.length() <= 2) return new ArrayList<>(Collections.nCopies(1536, 0.0));
        try {
            String clean = embText.substring(1, embText.length() - 1);
            return Arrays.stream(clean.split(","))
                    .map(String::trim)
                    .map(Double::parseDouble)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Error parseando vector, usando vector nulo", e);
            return new ArrayList<>(Collections.nCopies(1536, 0.0));
        }
    }

    private String truncateContent(String content) {
        if (content != null && content.length() > MAX_CONTENT_PREVIEW) {
            return content.substring(0, MAX_CONTENT_PREVIEW) + "...";
        }
        return content;
    }

    private record RawChunkData(String id, String content, List<Double> vector, String docName) {}
}
