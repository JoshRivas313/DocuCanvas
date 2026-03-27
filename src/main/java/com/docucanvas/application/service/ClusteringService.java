package com.docucanvas.application.service;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de Agrupamiento Semántico (Clustering) usando KMeans++.
 * Agrupa fragmentos similares para facilitar la exploración visual.
 */
@Service
public class ClusteringService {

    private static final Logger log = LoggerFactory.getLogger(ClusteringService.class);

    private static final List<String> COLORS = List.of(
            "#38bdf8", "#818cf8", "#fbbf24", "#34d399", "#f87171",
            "#a78bfa", "#fb7185", "#2dd4bf", "#f472b6", "#fb923c"
    );

    /**
     * Agrupa puntos en K clusters.
     * @param data puntos 3D proyectados
     * @param k número de clusters deseados
     * @return mapa de índice de punto -> índice de cluster
     */
    public int[] cluster(double[][] data, int k) {
        if (data == null || data.length < k) {
            log.warn("Datos insuficientes para Clustering, asignando todos al grupo 0");
            return new int[data != null ? data.length : 0];
        }

        log.info("Iniciando KMeans++ con k={}", k);
        List<DoublePoint> points = Arrays.stream(data)
                .map(DoublePoint::new)
                .collect(Collectors.toList());

        KMeansPlusPlusClusterer<DoublePoint> clusterer = new KMeansPlusPlusClusterer<>(k, 100);
        List<CentroidCluster<DoublePoint>> clusters = clusterer.cluster(points);

        int[] assignments = new int[data.length];
        for (int i = 0; i < clusters.size(); i++) {
            CentroidCluster<DoublePoint> cluster = clusters.get(i);
            for (DoublePoint point : cluster.getPoints()) {
                double[] pCoords = point.getPoint();
                // Buscamos el índice original del punto
                for (int j = 0; j < data.length; j++) {
                    if (isSamePoint(data[j], pCoords)) {
                        assignments[j] = i;
                        break;
                    }
                }
            }
        }

        log.info("Clustering completado. Clusters generados: {}", clusters.size());
        return assignments;
    }

    private boolean isSamePoint(double[] a, double[] b) {
        if (a.length != b.length) return false;
        double threshold = 1e-9;
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > threshold) return false;
        }
        return true;
    }

    public String getColor(int clusterIndex) {
        return COLORS.get(clusterIndex % COLORS.size());
    }

    /**
     * Genera un nombre descriptivo simple para el cluster basado en su contenido.
     */
    public String generateClusterName(int index, List<String> contents) {
        // En una implementación real usaríamos extracción de keywords o LLM.
        // Para la demo, tomamos la palabra más larga del primer chunk como "tema".
        if (contents.isEmpty()) return "Grupo " + index;
        
        String longestWord = Arrays.stream(contents.get(0).split("\\s+"))
                .map(s -> s.replaceAll("[^a-zA-ZáéíóúÁÉÍÓÚ]", ""))
                .filter(s -> s.length() > 4)
                .max(Comparator.comparingInt(String::length))
                .orElse("Tema " + index);
                
        String capitalized = longestWord.substring(0, 1).toUpperCase() + longestWord.substring(1).toLowerCase();
        return "Grupo " + index + ": " + capitalized;
    }
}
