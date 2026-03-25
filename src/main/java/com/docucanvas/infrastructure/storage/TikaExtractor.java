package com.docucanvas.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Component
public class TikaExtractor {

    private final Tika tika = new Tika();

    public String extractText(MultipartFile file) {
        try {
            log.debug("Extracting text from file: {}, size: {}", file.getOriginalFilename(), file.getSize());
            return tika.parseToString(file.getInputStream());
        } catch (Exception e) {
            log.error("Failed to extract text from file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Error extracting text: " + e.getMessage());
        }
    }
}
