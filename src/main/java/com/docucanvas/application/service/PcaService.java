package com.docucanvas.application.service;

import org.apache.commons.math3.linear.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio de Reducción de Dimensiones mediante Principal Component Analysis (PCA).
 * Transforma vectores de 1536 dimensiones (OpenAI) a 3 dimensiones para visualización.
 */
@Service
public class PcaService {

    private static final Logger log = LoggerFactory.getLogger(PcaService.class);

    public double[][] projectTo3D(double[][] data) {
        if (data == null || data.length < 3) {
            log.warn("Datos insuficientes para PCA, devolviendo ceros");
            return new double[data != null ? data.length : 0][3];
        }

        int n = data.length;
        int d = data[0].length;
        log.info("Iniciando PCA: Reduciendo {} vectores de {}d a 3d", n, d);

        // 1. Centrar los datos (Media Cero)
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

        // 2. Usar SVD (Singular Value Decomposition) para Proyección
        // Para pocos componentes (3), SVD es usualmente más estable y rápido que EigenDecomposition de la Covarianza
        RealMatrix matrix = MatrixUtils.createRealMatrix(centered);
        SingularValueDecomposition svd = new SingularValueDecomposition(matrix);
        
        // El resultado de la proyección son los primeros 3 componentes de U * S
        RealMatrix u = svd.getU();
        RealMatrix s = svd.getS();
        
        // Proyectamos: tomamos las primeras 3 columnas de U y las escalamos por los valores singulares
        double[][] projected = new double[n][3];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < 3; j++) {
                if (j < s.getRowDimension()) {
                    projected[i][j] = u.getEntry(i, j) * s.getEntry(j, j);
                }
            }
        }
        
        log.info("PCA (SVD) completado con éxito");
        return projected;
    }
    
    /**
     * Convierte una lista de listas de Double a un array bidimensional de double.
     */
    public double[][] convertToMatrix(List<List<Double>> vectors) {
        int n = vectors.size();
        int d = vectors.get(0).size();
        double[][] matrix = new double[n][d];
        for (int i = 0; i < n; i++) {
            List<Double> v = vectors.get(i);
            for (int j = 0; j < d; j++) {
                matrix[i][j] = v.get(j);
            }
        }
        return matrix;
    }
}
