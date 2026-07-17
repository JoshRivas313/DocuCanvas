package com.docucanvas.infrastructure.math;

import com.docucanvas.application.port.out.DimensionalityReductionPort;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reducción de dimensiones mediante PCA usando SVD (Singular Value Decomposition).
 * Adaptador de infraestructura que implementa {@link DimensionalityReductionPort}.
 */
@Component
public class PcaProjector implements DimensionalityReductionPort {

    private static final Logger log = LoggerFactory.getLogger(PcaProjector.class);
    private static final int TARGET_DIMENSIONS = 3;

    @Override
    public double[][] projectTo3D(double[][] data) {
        if (data == null || data.length < TARGET_DIMENSIONS) {
            log.warn("Datos insuficientes para PCA, devolviendo ceros");
            return new double[data != null ? data.length : 0][TARGET_DIMENSIONS];
        }

        int n = data.length;
        int d = data[0].length;
        log.info("Iniciando PCA: Reduciendo {} vectores de {}d a {}d", n, d, TARGET_DIMENSIONS);

        // 1. Centrar los datos (media cero por dimensión)
        double[] means = new double[d];
        for (int j = 0; j < d; j++) {
            double sum = 0;
            for (int i = 0; i < n; i++) sum += data[i][j];
            means[j] = sum / n;
        }

        double[][] centered = new double[n][d];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                centered[i][j] = data[i][j] - means[j];
            }
        }

        // 2. SVD: para pocos componentes es más estable/rápido que la eigendescomposición
        RealMatrix matrix = MatrixUtils.createRealMatrix(centered);
        SingularValueDecomposition svd = new SingularValueDecomposition(matrix);
        RealMatrix u = svd.getU();
        RealMatrix s = svd.getS();

        // 3. Proyección: primeras 3 columnas de U escaladas por sus valores singulares
        double[][] projected = new double[n][TARGET_DIMENSIONS];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < TARGET_DIMENSIONS; j++) {
                if (j < s.getRowDimension()) {
                    projected[i][j] = u.getEntry(i, j) * s.getEntry(j, j);
                }
            }
        }

        log.info("PCA (SVD) completado con éxito");
        return projected;
    }
}
