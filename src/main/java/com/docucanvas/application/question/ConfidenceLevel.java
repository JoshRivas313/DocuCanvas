package com.docucanvas.application.question;

/**
 * Nivel de confianza de una respuesta RAG, derivado del score promedio de
 * similitud coseno de los chunks recuperados.
 *
 * <p>Los umbrales están calibrados a la distribución <b>real</b> observada en
 * este proyecto con {@code nomic-embed-text} sobre texto en español — no a
 * porcentajes ilustrativos genéricos. En pruebas con documentos reales, un
 * match claramente correcto anotó 0.58–0.62 de similitud, mientras que un
 * chunk del documento equivocado (recuperado por el bug de índice ya
 * corregido) anotó 0.51–0.54. Ese es el rango real en el que este modelo de
 * embeddings opera; un umbral "alta confianza ≥0.90" nunca se activaría.
 */
public enum ConfidenceLevel {
    ALTA, MEDIA, BAJA;

    private static final double ALTA_MIN = 0.55;
    private static final double MEDIA_MIN = 0.40;

    public static ConfidenceLevel fromAverageScore(double avgScore) {
        if (avgScore >= ALTA_MIN) return ALTA;
        if (avgScore >= MEDIA_MIN) return MEDIA;
        return BAJA;
    }
}
