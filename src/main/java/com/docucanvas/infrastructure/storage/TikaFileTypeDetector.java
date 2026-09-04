package com.docucanvas.infrastructure.storage;

import com.docucanvas.application.port.out.FileTypeDetectorPort;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * Detección de tipo real por contenido (magic bytes) usando Apache Tika.
 *
 * <p>Tika inspecciona la firma binaria del archivo; el nombre solo se pasa como
 * pista secundaria para desempatar formatos textuales que no tienen firma
 * propia (un {@code .md} y un {@code .txt} son bytes indistinguibles).
 */
@Component
public class TikaFileTypeDetector implements FileTypeDetectorPort {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(TikaFileTypeDetector.class);

    private static final String UNKNOWN = "application/octet-stream";

    private final Tika tika = new Tika();

    @Override
    public String detectMediaType(byte[] content, String filename) {
        if (content == null || content.length == 0) {
            return UNKNOWN;
        }
        try {
            return tika.detect(content, filename);
        } catch (Exception e) {
            log.warn("No se pudo detectar el tipo real de '{}': {}", filename, e.getMessage());
            return UNKNOWN;
        }
    }
}
