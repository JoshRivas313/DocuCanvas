package com.docucanvas.application.service;

import com.docucanvas.application.chunk.ChunkView;
import com.docucanvas.application.chunk.ClusterName;
import com.docucanvas.application.chunk.ClusterPalette;
import com.docucanvas.application.chunk.RawChunk;
import com.docucanvas.application.port.out.ChunkReadPort;
import com.docucanvas.application.port.out.ClusterNamingPort;
import com.docucanvas.application.port.out.ClusteringPort;
import com.docucanvas.application.port.out.DimensionalityReductionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orquesta la vista de chunks: lectura (puerto) → reducción PCA (puerto) →
 * clustering (puerto) → nombrado (puerto) → ensamblado del {@link ChunkView}.
 *
 * <p>No contiene detalles de persistencia ni de IA: solo coordina puertos de
 * salida, de acuerdo con la arquitectura hexagonal.
 */
@Service
public class ChunkService {

    private static final Logger log = LoggerFactory.getLogger(ChunkService.class);
    private static final int MAX_CONTENT_PREVIEW = 100;
    private static final int MAX_CHUNKS_VISUALIZE = 300;
    private static final int MAX_CLUSTERS = 10;
    private static final int MIN_CLUSTERS = 2;
    private static final int CHUNKS_PER_CLUSTER = 5;
    private static final int DEFAULT_SEARCH_TOP_K = 5;

    private final ChunkReadPort chunkReadPort;
    private final DimensionalityReductionPort dimensionalityReduction;
    private final ClusteringPort clustering;
    private final ClusterNamingPort clusterNaming;
    private final VectorStore vectorStore;

    public ChunkService(ChunkReadPort chunkReadPort,
                        DimensionalityReductionPort dimensionalityReduction,
                        ClusteringPort clustering,
                        ClusterNamingPort clusterNaming,
                        VectorStore vectorStore) {
        this.chunkReadPort = chunkReadPort;
        this.dimensionalityReduction = dimensionalityReduction;
        this.clustering = clustering;
        this.clusterNaming = clusterNaming;
        this.vectorStore = vectorStore;
    }

    /**
     * Búsqueda semántica de chunks por texto libre.
     *
     * @return IDs de los chunks más similares
     */
    public List<String> searchSimilarChunkIds(String query) {
        return vectorStore
                .similaritySearch(SearchRequest.builder().query(query).topK(DEFAULT_SEARCH_TOP_K).build())
                .stream()
                .map(Document::getId)
                .toList();
    }

    /**
     * Recupera chunks y aplica PCA + Clustering dinámico para la vista global.
     */
    @Cacheable(value = "chunksGlobal")
    public List<ChunkView> getChunksForVisualization() {
        log.info("Calculando proyección 3D con PCA y Clustering dinámico para vista global (CACHE MISS)");
        List<RawChunk> rawData = chunkReadPort.findForGlobalView(MAX_CHUNKS_VISUALIZE);
        if (rawData.isEmpty()) return List.of();
        return processAndCluster(rawData);
    }

    /**
     * Recupera todos los chunks asociados a un documento con su proyección PCA.
     */
    @Cacheable(value = "chunksDoc", key = "#documentId")
    public List<ChunkView> getChunksByDocumentId(String documentId) {
        log.info("Generando vista de indexación para documento: {} (CACHE MISS)", documentId);
        List<RawChunk> rawData = chunkReadPort.findByDocument(documentId);
        if (rawData.isEmpty()) return List.of();
        return processAndCluster(rawData);
    }

    private List<ChunkView> processAndCluster(List<RawChunk> rawData) {
        // 1. Reducción de dimensiones (PCA/SVD → 3D)
        double[][] matrix = rawData.stream().map(RawChunk::embedding).toArray(double[][]::new);
        double[][] projected = dimensionalityReduction.projectTo3D(matrix);

        // 2. Clustering (KMeans++ con k dinámico)
        int k = resolveClusterCount(rawData.size());
        int[] clusterAssignments = clustering.cluster(projected, k);

        // 3. Agrupar contenidos por cluster para el nombrado semántico
        Map<Integer, List<String>> clusterContents = new HashMap<>();
        for (int i = 0; i < rawData.size(); i++) {
            clusterContents.computeIfAbsent(clusterAssignments[i], v -> new ArrayList<>())
                    .add(rawData.get(i).content());
        }

        Map<Integer, ClusterName> clusterNames = new HashMap<>();
        for (int i = 0; i < k; i++) {
            clusterNames.put(i, clusterNaming.name(i, clusterContents.getOrDefault(i, List.of())));
        }

        // 4. Ensamblar la vista
        List<ChunkView> result = new ArrayList<>(rawData.size());
        for (int i = 0; i < rawData.size(); i++) {
            RawChunk raw = rawData.get(i);
            int clusterIdx = clusterAssignments[i];
            ClusterName meta = clusterNames.get(clusterIdx);

            result.add(new ChunkView(
                    raw.id(),
                    truncateContent(raw.content()),
                    raw.content(),
                    Arrays.stream(projected[i]).boxed().toList(),
                    raw.documentName() != null ? raw.documentName() : "Desconocido",
                    meta.name(),
                    meta.description(),
                    ClusterPalette.colorFor(clusterIdx)));
        }
        return result;
    }

    /**
     * k dinámico: escalado agresivo para que en demos pequeñas (3-10 chunks) se
     * distingan los grupos. Con ≤3 chunks, un grupo por chunk.
     */
    private int resolveClusterCount(int chunkCount) {
        if (chunkCount <= 3) return chunkCount;
        return Math.min(MAX_CLUSTERS,
                Math.max(MIN_CLUSTERS, (int) Math.ceil(chunkCount / (double) CHUNKS_PER_CLUSTER)));
    }

    private String truncateContent(String content) {
        if (content != null && content.length() > MAX_CONTENT_PREVIEW) {
            return content.substring(0, MAX_CONTENT_PREVIEW) + "...";
        }
        return content;
    }
}
