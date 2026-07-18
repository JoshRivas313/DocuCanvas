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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests del pipeline de ensamblado de {@link ChunkService} con los puertos de
 * salida mockeados (lectura → PCA → clustering → naming → {@link ChunkView}).
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
    @DisplayName("Debe ensamblar un ChunkView por chunk con coordenadas, cluster y color deterministas")
    void ensamblaVistaCompleta() {
        String largo = "a".repeat(150); // > 100 para verificar el truncado del preview
        List<RawChunk> raw = List.of(
                new RawChunk("id-0", largo, new double[]{1, 0, 0}, "Doc A"),
                new RawChunk("id-1", "corto", new double[]{0, 1, 0}, "Doc B"));

        when(chunkReadPort.findByDocument("doc")).thenReturn(raw);
        when(dimensionalityReduction.projectTo3D(any())).thenReturn(new double[][]{{1.5, 0, 0}, {-1.5, 0, 0}});
        when(clustering.cluster(any(), eq(2))).thenReturn(new int[]{0, 1});
        when(clusterNaming.name(eq(0), any())).thenReturn(new ClusterName("Grupo 0: Alfa", "desc alfa"));
        when(clusterNaming.name(eq(1), any())).thenReturn(new ClusterName("Grupo 1: Beta", "desc beta"));

        List<ChunkView> views = service().getChunksByDocumentId("doc");

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
    @DisplayName("Sin chunks debe devolver lista vacía sin invocar PCA ni clustering")
    void sinChunksDevuelveVacio() {
        when(chunkReadPort.findByDocument("vacio")).thenReturn(List.of());

        List<ChunkView> views = service().getChunksByDocumentId("vacio");

        assertThat(views).isEmpty();
    }

    @Test
    @DisplayName("documentName nulo debe caer a 'Desconocido'")
    void documentNameNuloUsaDesconocido() {
        List<RawChunk> raw = List.of(new RawChunk("id-0", "texto", new double[]{1, 0, 0}, null));
        when(chunkReadPort.findByDocument("doc")).thenReturn(raw);
        when(dimensionalityReduction.projectTo3D(any())).thenReturn(new double[][]{{0, 0, 0}});
        when(clustering.cluster(any(), anyInt())).thenReturn(new int[]{0});
        when(clusterNaming.name(anyInt(), any())).thenReturn(new ClusterName("Grupo 0", "d"));

        List<ChunkView> views = service().getChunksByDocumentId("doc");

        assertThat(views.get(0).documentName()).isEqualTo("Desconocido");
    }
}
