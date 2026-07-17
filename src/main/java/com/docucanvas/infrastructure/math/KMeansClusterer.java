package com.docucanvas.infrastructure.math;

import com.docucanvas.application.port.out.ClusteringPort;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrupamiento KMeans++ sobre puntos ya proyectados. Adaptador de infraestructura
 * que implementa {@link ClusteringPort}.
 *
 * <p>Cada punto se envuelve junto a su índice original ({@link IndexedPoint}),
 * de modo que la asignación se recupera en O(n) sin comparar coordenadas.
 */
@Component
public class KMeansClusterer implements ClusteringPort {

    private static final Logger log = LoggerFactory.getLogger(KMeansClusterer.class);
    private static final int MAX_ITERATIONS = 100;

    @Override
    public int[] cluster(double[][] data, int k) {
        if (data == null || data.length < k) {
            log.warn("Datos insuficientes para Clustering, asignando todos al grupo 0");
            return new int[data != null ? data.length : 0];
        }

        log.info("Iniciando KMeans++ con k={}", k);
        List<IndexedPoint> points = new ArrayList<>(data.length);
        for (int i = 0; i < data.length; i++) {
            points.add(new IndexedPoint(data[i], i));
        }

        KMeansPlusPlusClusterer<IndexedPoint> clusterer =
                new KMeansPlusPlusClusterer<>(k, MAX_ITERATIONS);
        List<CentroidCluster<IndexedPoint>> clusters = clusterer.cluster(points);

        int[] assignments = new int[data.length];
        for (int c = 0; c < clusters.size(); c++) {
            for (IndexedPoint p : clusters.get(c).getPoints()) {
                assignments[p.index()] = c;
            }
        }

        log.info("Clustering completado. Clusters generados: {}", clusters.size());
        return assignments;
    }

    /** Punto que conserva su índice original para recuperar la asignación en O(n). */
    private record IndexedPoint(double[] coords, int index) implements Clusterable {
        @Override
        public double[] getPoint() {
            return coords;
        }
    }
}
