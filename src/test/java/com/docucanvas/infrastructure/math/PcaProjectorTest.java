package com.docucanvas.infrastructure.math;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link PcaProjector}. Incluye la regresión del fix del Sprint 2:
 * con n=2 puntos la proyección SVD de rango 1 debe separarlos (antes el guard
 * n&lt;3 los colapsaba al origen y KMeans no podía distinguirlos).
 */
@DisplayName("PcaProjector — Reducción SVD a 3D")
class PcaProjectorTest {

    private final PcaProjector projector = new PcaProjector();

    @Test
    @DisplayName("Con 2 vectores distintos debe producir 2 puntos separados y simétricos (regresión fix n=2)")
    void dosVectoresSeSeparan() {
        double[][] data = {
                {1.0, 0.0, 0.0, 0.0},
                {0.0, 1.0, 0.0, 0.0}
        };

        double[][] projected = projector.projectTo3D(data);

        assertThat(projected).hasDimensions(2, 3);
        // Rango 1: separados sobre el primer eje, simétricos respecto al origen
        assertThat(projected[0][0]).isNotZero();
        assertThat(projected[1][0]).isCloseTo(-projected[0][0], org.assertj.core.data.Offset.offset(1e-9));
        // No deben coincidir (el bug original los dejaba a ambos en el origen)
        assertThat(projected[0]).isNotEqualTo(projected[1]);
    }

    @Test
    @DisplayName("Con 1 punto debe devolver el origen (no hay varianza que proyectar)")
    void unPuntoVaAlOrigen() {
        double[][] projected = projector.projectTo3D(new double[][]{{5.0, 3.0, 1.0}});

        assertThat(projected).hasDimensions(1, 3);
        assertThat(projected[0]).containsExactly(0.0, 0.0, 0.0);
    }

    @Test
    @DisplayName("Con datos nulos o vacíos debe devolver una matriz vacía")
    void datosVaciosDevuelvenVacio() {
        assertThat(projector.projectTo3D(null)).isEmpty();
        assertThat(projector.projectTo3D(new double[0][0])).isEmpty();
    }

    @Test
    @DisplayName("Con n>=3 vectores debe proyectar todos a 3 dimensiones conservando separación")
    void variosVectoresConservanSeparacion() {
        double[][] data = {
                {10.0, 0.0, 0.0, 0.0},
                {0.0, 10.0, 0.0, 0.0},
                {0.0, 0.0, 10.0, 0.0},
                {10.0, 0.1, 0.0, 0.0}
        };

        double[][] projected = projector.projectTo3D(data);

        assertThat(projected).hasDimensions(4, 3);
        // El punto 3 (casi idéntico al 0) debe quedar mucho más cerca del 0 que del 1
        double dist03 = dist(projected[0], projected[3]);
        double dist13 = dist(projected[1], projected[3]);
        assertThat(dist03).isLessThan(dist13);
    }

    private double dist(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += (a[i] - b[i]) * (a[i] - b[i]);
        return Math.sqrt(sum);
    }
}
