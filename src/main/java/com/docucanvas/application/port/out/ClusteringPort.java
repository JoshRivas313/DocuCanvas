package com.docucanvas.application.port.out;

/**
 * Puerto de salida para agrupar puntos en {@code k} clusters. La implementación
 * concreta (KMeans++) vive en infraestructura.
 */
public interface ClusteringPort {

    /**
     * @param points puntos (típicamente 3D ya proyectados)
     * @param k      número de clusters deseado
     * @return array donde la posición {@code i} es el cluster asignado al punto {@code i}
     */
    int[] cluster(double[][] points, int k);
}
