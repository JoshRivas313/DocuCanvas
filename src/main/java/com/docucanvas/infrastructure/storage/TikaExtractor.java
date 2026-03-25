package com.docucanvas.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class TikaExtractor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TikaExtractor.class);
    private final Tika tika = new Tika();

    public String extractText(byte[] content, String filename) {
        try {
            log.debug("Extracting text from file: {}, size: {}", filename, content.length);
            return tika.parseToString(new java.io.ByteArrayInputStream(content));
        } catch (Exception e) {
            log.error("Failed to extract text from file: {}", filename, e);
            throw new RuntimeException("Error extracting text: " + e.getMessage());
        }
    }
}

