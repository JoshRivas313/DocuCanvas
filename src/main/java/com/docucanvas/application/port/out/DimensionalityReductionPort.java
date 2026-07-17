package com.docucanvas.application.port.out;

/**
 * Puerto de salida para reducir vectores de alta dimensión a 3D (visualización).
 * La implementación concreta (PCA vía SVD) vive en infraestructura.
 */
public interface DimensionalityReductionPort {

    /**
     * Proyecta una matriz {@code n x d} a {@code n x 3}.
     *
     * @param vectors matriz de {@code n} vectores de dimensión {@code d}
     * @return matriz {@code n x 3} con las coordenadas proyectadas
     */
    double[][] projectTo3D(double[][] vectors);
}
