package com.docucanvas.application.chunk;

import java.util.List;

/**
 * Paleta de colores estable para los clusters de la visualización.
 * El color se asigna de forma determinista por índice de cluster.
 */
public final class ClusterPalette {

    private static final List<String> COLORS = List.of(
            "#38bdf8", "#818cf8", "#fbbf24", "#34d399", "#f87171",
            "#a78bfa", "#fb7185", "#2dd4bf", "#f472b6", "#fb923c"
    );

    private ClusterPalette() {}

    public static String colorFor(int clusterIndex) {
        return COLORS.get(Math.floorMod(clusterIndex, COLORS.size()));
    }
}
