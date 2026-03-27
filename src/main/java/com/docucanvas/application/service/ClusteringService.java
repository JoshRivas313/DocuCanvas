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

    private final org.springframework.ai.chat.client.ChatClient chatClient;
    private static final List<String> COLORS = List.of(
            "#38bdf8", "#818cf8", "#fbbf24", "#34d399", "#f87171",
            "#a78bfa", "#fb7185", "#2dd4bf", "#f472b6", "#fb923c"
    );

    public ClusteringService(org.springframework.ai.chat.client.ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

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
        if (contents.isEmpty()) return "Grupo " + index;
        
        try {
            // Tomamos una muestra de los primeros 3 fragmentos para el análisis
            String sample = contents.stream()
                    .limit(3)
                    .map(s -> s.substring(0, Math.min(s.length(), 200)))
                    .collect(Collectors.joining("\n--- \n"));

            String topic = chatClient.prompt()
                    .system("Eres un clasificador de temas. Responde solo con una o dos palabras que definan el tema.")
                    .user("Define el tema central de estos fragmentos:\n" + sample)
                    .call()
                    .content();

            // Limpieza del resultado
            topic = topic.replaceAll("[^a-zA-ZáéíóúÁÉÍÓÚ\\s]", "").trim();
            if (topic.length() > 25) topic = topic.substring(0, 25);
            if (topic.isEmpty()) return fallbackClusterName(index, contents);

            String capitalized = topic.substring(0, 1).toUpperCase() + topic.substring(1).toLowerCase();
            return "Grupo " + index + ": " + capitalized;

        } catch (Exception e) {
            log.warn("Fallo en IA para nombrar cluster {}, usando heurístico", index);
            return fallbackClusterName(index, contents);
        }
    }

    private String fallbackClusterName(int index, List<String> contents) {
        String longestWord = Arrays.stream(contents.get(0).split("\\s+"))
                .map(s -> s.replaceAll("[^a-zA-ZáéíóúÁÉÍÓÚ]", ""))
                .filter(s -> s.length() > 4)
                .max(Comparator.comparingInt(String::length))
                .orElse("Tema " + index);
                
        String capitalized = longestWord.substring(0, 1).toUpperCase() + longestWord.substring(1).toLowerCase();
        return "Grupo " + index + ": " + capitalized;
    }
}
