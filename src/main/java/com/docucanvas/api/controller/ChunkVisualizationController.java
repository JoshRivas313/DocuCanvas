package com.docucanvas.api.controller;

import com.docucanvas.api.dto.ChunkDTO;
import com.docucanvas.application.chunk.ChunkView;
import com.docucanvas.application.service.ChunkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador de visualización e inspección del VectorStore.
 *
 * <p>Expone los endpoints que el frontend 3D utiliza para explorar los chunks
 * vectorizados. Traduce el modelo de aplicación ({@link ChunkView}) al DTO de la
 * API ({@link ChunkDTO}).
 */
@RestController
@RequestMapping("/api/v1/chunks")
public class ChunkVisualizationController {

    private final ChunkService chunkService;

    public ChunkVisualizationController(ChunkService chunkService) {
        this.chunkService = chunkService;
    }

    @GetMapping("/visualize")
    public List<ChunkDTO> getChunks() {
        return chunkService.getChunksForVisualization().stream().map(this::toDto).toList();
    }

    @GetMapping("/document/{documentId}")
    public List<ChunkDTO> getChunksByDocumentId(@PathVariable String documentId) {
        return chunkService.getChunksByDocumentId(documentId).stream().map(this::toDto).toList();
    }

    @GetMapping("/search")
    public List<String> searchChunks(@RequestParam("query") String query) {
        return chunkService.searchSimilarChunkIds(query);
    }

    private ChunkDTO toDto(ChunkView v) {
        return new ChunkDTO(
                v.id(),
                v.preview(),
                v.fullContent(),
                v.coordinates(),
                v.documentName(),
                v.clusterName(),
                v.clusterDescription(),
                v.clusterColor());
    }
}
