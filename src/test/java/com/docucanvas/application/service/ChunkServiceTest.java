package com.docucanvas.application.service;

import com.docucanvas.application.chunk.ChunkView;
import com.docucanvas.application.chunk.ClusterName;
import com.docucanvas.application.chunk.ClusterPalette;
import com.docucanvas.application.chunk.RawChunk;
import com.docucanvas.application.port.out.ChunkReadPort;
import com.docucanvas.application.port.out.ClusterNamingPort;
import com.docucanvas.application.port.out.ClusteringPort;
import com.docucanvas.application.port.out.DimensionalityReductionPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests del pipeline de ensamblado de {@link ChunkService}, con los puertos
 * de salida mockeados.
 *
 * <p>Cubre dos caminos deliberadamente distintos: {@link
 * ChunkService#getChunksForVisualization()} (lectura → PCA → clustering →
 * naming → {@link ChunkView}, para la Vista de Embeddings) y {@link
 * ChunkService#getChunksByDocumentId(String)} (lectura ligera sin PCA ni
 * clustering, para la Vista de Indexación).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChunkService — Ensamblado de la vista de chunks")
class ChunkServiceTest {

    @Mock private ChunkReadPort chunkReadPort;
    @Mock private DimensionalityReductionPort dimensionalityReduction;
    @Mock private ClusteringPort clustering;
    @Mock private ClusterNamingPort clusterNaming;
    @Mock private VectorStore vectorStore;

    private ChunkService service() {
        return new ChunkService(chunkReadPort, dimensionalityReduction, clustering, clusterNaming, vectorStore);
    }

    @Test
    @DisplayName("getChunksForVisualization ensambla un ChunkView por chunk con coordenadas, cluster y color")
    void visualizacionEnsamblaVistaCompleta() {
        String largo = "a".repeat(150); // > 100 para verificar el truncado del preview
        List<RawChunk> raw = List.of(
                new RawChunk("id-0", largo, new double[]{1, 0, 0}, "Doc A"),
                new RawChunk("id-1", "corto", new double[]{0, 1, 0}, "Doc B"));

        when(chunkReadPort.findForGlobalView(anyInt())).thenReturn(raw);
        when(dimensionalityReduction.projectTo3D(any())).thenReturn(new double[][]{{1.5, 0, 0}, {-1.5, 0, 0}});
        when(clustering.cluster(any(), eq(2))).thenReturn(new int[]{0, 1});
        when(clusterNaming.nameAll(any())).thenReturn(Map.of(
                0, new ClusterName("Grupo 0: Alfa", "desc alfa"),
                1, new ClusterName("Grupo 1: Beta", "desc beta")));

        List<ChunkView> views = service().getChunksForVisualization();

        assertThat(views).hasSize(2);

        ChunkView v0 = views.get(0);
        assertThat(v0.id()).isEqualTo("id-0");
        assertThat(v0.preview()).hasSize(103).endsWith("..."); // 100 chars + "..."
        assertThat(v0.fullContent()).isEqualTo(largo);
        assertThat(v0.coordinates()).containsExactly(1.5, 0.0, 0.0);
        assertThat(v0.documentName()).isEqualTo("Doc A");
        assertThat(v0.clusterName()).isEqualTo("Grupo 0: Alfa");
        assertThat(v0.clusterColor()).isEqualTo(ClusterPalette.colorFor(0));

        ChunkView v1 = views.get(1);
        assertThat(v1.clusterName()).isEqualTo("Grupo 1: Beta");
        assertThat(v1.clusterColor()).isEqualTo(ClusterPalette.colorFor(1));
        assertThat(v1.documentName()).isEqualTo("Doc B");
    }

    @Test
    @DisplayName("getChunksForVisualization sin chunks devuelve lista vacía sin invocar PCA ni clustering")
    void visualizacionSinChunksDevuelveVacio() {
        when(chunkReadPort.findForGlobalView(anyInt())).thenReturn(List.of());

        List<ChunkView> views = service().getChunksForVisualization();

        assertThat(views).isEmpty();
        verifyNoInteractions(dimensionalityReduction, clustering, clusterNaming);
    }

    @Test
    @DisplayName("getChunksByDocumentId es una lectura ligera: no invoca PCA, clustering ni naming por LLM")
    void indexacionEsLecturaLigera() {
        List<RawChunk> raw = List.of(
                new RawChunk("id-0", "contenido del chunk", new double[]{1, 0, 0}, "Doc A"));
        when(chunkReadPort.findByDocument("doc")).thenReturn(raw);

        List<ChunkView> views = service().getChunksByDocumentId("doc");

        assertThat(views).hasSize(1);
        assertThat(views.get(0).id()).isEqualTo("id-0");
        assertThat(views.get(0).fullContent()).isEqualTo("contenido del chunk");
        // Sin proyección 3D real ni metadata de cluster: esta vista no los necesita.
        assertThat(views.get(0).coordinates()).containsExactly(0.0, 0.0, 0.0);
        assertThat(views.get(0).clusterName()).isEmpty();

        // La garantía de rendimiento: cero llamadas al pipeline pesado.
        verifyNoInteractions(dimensionalityReduction, clustering, clusterNaming);
    }

    @Test
    @DisplayName("getChunksByDocumentId: documentName nulo cae a 'Desconocido'")
    void documentNameNuloUsaDesconocido() {
        List<RawChunk> raw = List.of(new RawChunk("id-0", "texto", new double[]{1, 0, 0}, null));
        when(chunkReadPort.findByDocument("doc")).thenReturn(raw);

        List<ChunkView> views = service().getChunksByDocumentId("doc");

        assertThat(views.get(0).documentName()).isEqualTo("Desconocido");
    }
}
