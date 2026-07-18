package com.docucanvas.infrastructure.math;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests de {@link KMeansClusterer}: separación de grupos obvios y casos degenerados. */
@DisplayName("KMeansClusterer — Agrupamiento KMeans++")
class KMeansClustererTest {

    private final KMeansClusterer clusterer = new KMeansClusterer();

    @Test
    @DisplayName("Debe separar dos grupos claramente distintos en clusters diferentes")
    void separaDosGruposObvios() {
        double[][] points = {
                {0.0, 0.0, 0.0},
                {0.1, 0.0, 0.1},
                {10.0, 10.0, 10.0},
                {10.1, 10.0, 9.9}
        };

        int[] assignments = clusterer.cluster(points, 2);

        assertThat(assignments).hasSize(4);
        // Los dos primeros juntos, los dos últimos juntos, y en clusters distintos
        assertThat(assignments[0]).isEqualTo(assignments[1]);
        assertThat(assignments[2]).isEqualTo(assignments[3]);
        assertThat(assignments[0]).isNotEqualTo(assignments[2]);
    }

    @Test
    @DisplayName("Con menos puntos que k debe asignar todos al grupo 0 sin fallar")
    void datosInsuficientesVanAlGrupoCero() {
        int[] assignments = clusterer.cluster(new double[][]{{1.0, 2.0, 3.0}}, 5);

        assertThat(assignments).containsExactly(0);
    }

    @Test
    @DisplayName("Cada asignación debe estar en el rango [0, k)")
    void asignacionesEnRango() {
        double[][] points = {
                {0, 0, 0}, {1, 1, 1}, {2, 2, 2}, {8, 8, 8}, {9, 9, 9}, {10, 10, 10}
        };

        int[] assignments = clusterer.cluster(points, 3);

        assertThat(assignments).hasSize(6);
        for (int a : assignments) {
            assertThat(a).isBetween(0, 2);
        }
    }
}
