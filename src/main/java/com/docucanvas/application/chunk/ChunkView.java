package com.docucanvas.application.chunk;

import java.util.List;

/**
 * Resultado de aplicación para la visualización de un chunk: contenido,
 * coordenadas 3D (PCA) y metadatos del cluster asignado. El controlador lo
 * traduce al DTO de la API.
 */
public record ChunkView(
        String id,
        String preview,
        String fullContent,
        List<Double> coordinates,
        String documentName,
        String clusterName,
        String clusterDescription,
        String clusterColor
) {}
