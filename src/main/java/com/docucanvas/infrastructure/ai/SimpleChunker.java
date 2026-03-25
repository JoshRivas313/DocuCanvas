package com.docucanvas.infrastructure.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SimpleChunker {

    private static final int CHUNK_SIZE = 1000;
    private static final int OVERLAP = 200;

    public List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        int start = 0;
        int textLength = text.length();

        while (start < textLength) {
            int end = Math.min(start + CHUNK_SIZE, textLength);
            chunks.add(text.substring(start, end));
            
            if (end == textLength) break;
            start = end - OVERLAP;
        }
        return chunks;
    }
}
