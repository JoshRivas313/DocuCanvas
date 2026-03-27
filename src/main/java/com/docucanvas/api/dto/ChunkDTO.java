package com.docucanvas.api.dto;

import java.util.List;

public record ChunkDTO(
    String id, 
    String content, 
    List<Double> coordinates, 
    String documentName,
    String clusterName,
    String clusterColor
) {
}
