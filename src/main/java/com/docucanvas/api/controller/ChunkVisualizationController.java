package com.docucanvas.api.controller;

import com.docucanvas.api.dto.ChunkDTO;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/chunks")
public class ChunkVisualizationController {

    private final JdbcClient jdbcClient;
    private final VectorStore vectorStore;

    public ChunkVisualizationController(JdbcClient jdbcClient, VectorStore vectorStore) {
        this.jdbcClient = jdbcClient;
        this.vectorStore = vectorStore;
    }

    @GetMapping("/visualize")
    public List<ChunkDTO> getChunks() {
        return jdbcClient.sql("SELECT id, content, embedding::text AS emb_text, metadata->>'file_name' AS doc_name FROM document_chunks LIMIT 50")
                .query((rs, rowNum) -> {
                    String id = rs.getString("id");
                    String content = rs.getString("content");
                    if (content != null && content.length() > 100) {
                        content = content.substring(0, 100) + "...";
                    }
                    
                    String docName = rs.getString("doc_name");
                    if (docName == null) docName = "Desconocido";

                    String embText = rs.getString("emb_text");
                    List<Double> coords = List.of(0.0, 0.0, 0.0);
                    if (embText != null && embText.length() > 2) {
                        try {
                            String clean = embText.substring(1, embText.length() - 1);
                            String[] parts = clean.split(",");
                            coords = Arrays.stream(parts)
                                    .limit(3)
                                    .map(String::trim)
                                    .map(Double::parseDouble)
                                    .collect(Collectors.toList());
                        } catch (Exception e) {
                            // ignore parsing errors, use defaults
                        }
                    }
                    return new ChunkDTO(id, content, coords, docName);
                })
                .list();
    }

    @GetMapping("/search")
    public List<String> searchChunks(@RequestParam("query") String query) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(5).build())
                .stream()
                .map(org.springframework.ai.document.Document::getId)
                .toList();
    }
}
