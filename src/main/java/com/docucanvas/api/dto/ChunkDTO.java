package com.docucanvas.api.dto;

import java.util.List;

public record ChunkDTO(
    String id, 
    String content, 
    String fullContent,
    List<Double> coordinates, 
    String documentName,
    String clusterName,
    String clusterDescription,
    String clusterColor
) {
}
