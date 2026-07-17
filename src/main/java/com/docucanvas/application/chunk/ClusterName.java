package com.docucanvas.application.chunk;

/**
 * Nombre y descripción semánticos de un cluster. Producido por
 * {@link com.docucanvas.application.port.out.ClusterNamingPort}.
 */
public record ClusterName(String name, String description) {}
