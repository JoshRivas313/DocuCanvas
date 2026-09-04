package com.docucanvas.application.port.out;

/**
 * Detecta el tipo real de un archivo a partir de sus bytes, no de su nombre.
 *
 * <p>Puerto de salida: la capa de aplicación decide <em>qué</em> política de
 * tipos aplica; la infraestructura decide <em>cómo</em> se detecta el tipo
 * (hoy, los magic bytes vía Apache Tika).
 */
public interface FileTypeDetectorPort {

    /**
     * @param content  bytes del archivo
     * @param filename nombre original, usado solo como pista secundaria
     * @return media type detectado (p.ej. {@code application/pdf}), nunca {@code null}
     */
    String detectMediaType(byte[] content, String filename);
}
