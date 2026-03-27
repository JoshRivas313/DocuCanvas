package com.docucanvas.api.controller;

import com.docucanvas.api.dto.ChunkDTO;
import com.docucanvas.application.service.ChunkService;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador de visualización e inspección del VectorStore.
 *
 * <p>Expone endpoints que el frontend 3D utiliza para explorar
 * los chunks vectorizados almacenados en PGVector.
 */
@RestController
@RequestMapping("/api/v1/chunks")
public class ChunkVisualizationController {

    private final ChunkService chunkService;
    private final VectorStore vectorStore;

    public ChunkVisualizationController(ChunkService chunkService, VectorStore vectorStore) {
        this.chunkService = chunkService;
        this.vectorStore = vectorStore;
    }

    /**
     * Retorna hasta 50 chunks con sus primeras 3 dimensiones vectoriales
     * para renderizado en el espacio 3D del frontend.
     */
    @GetMapping("/visualize")
    public List<ChunkDTO> getChunks() {
        return chunkService.getChunksForVisualization();
    }

    /**
     * Búsqueda semántica de chunks por texto libre.
     *
     * @param query texto de búsqueda semántica
     * @return lista de IDs de chunks más similares
     */
    @GetMapping("/search")
    public List<String> searchChunks(@RequestParam("query") String query) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(5).build())
                .stream()
                .map(org.springframework.ai.document.Document::getId)
                .toList();
    }
}

