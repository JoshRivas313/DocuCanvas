package com.docucanvas.application.port.out;

import com.docucanvas.application.chunk.ClusterName;

import java.util.List;
import java.util.Map;

/**
 * Puerto de salida para nombrar semánticamente un conjunto de clusters a
 * partir de una muestra de sus contenidos. La implementación concreta (LLM u
 * otra) vive en infraestructura.
 *
 * <p>El contrato es por lote (todos los clusters de una vez) y no por
 * cluster individual a propósito: nombrar k clusters con k llamadas
 * separadas al modelo multiplica la latencia por k, y esa multiplicación no
 * se resuelve solo con paralelismo del lado del cliente si el servidor de
 * inferencia (p.ej. Ollama en CPU) serializa las peticiones concurrentes.
 * Pedir los k nombres en una única llamada elimina el problema de raíz.
 */
public interface ClusterNamingPort {

    Map<Integer, ClusterName> nameAll(Map<Integer, List<String>> clusterContentsByIndex);
}
