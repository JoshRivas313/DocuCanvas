package com.docucanvas.application.port.out;

import com.docucanvas.application.chunk.ClusterName;

import java.util.List;

/**
 * Puerto de salida para nombrar semánticamente un cluster a partir de una
 * muestra de sus contenidos. La implementación concreta (LLM u otra) vive en
 * infraestructura.
 */
public interface ClusterNamingPort {

    ClusterName name(int clusterIndex, List<String> contents);
}
