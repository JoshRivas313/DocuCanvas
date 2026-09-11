package com.docucanvas.application.visual;

/**
 * Representación visual producida para una respuesta RAG.
 *
 * @param url    URL de la imagen, o Data URL {@code data:...} si es contenido inline
 * @param source qué mecanismo la produjo realmente
 */
public record VisualRendering(String url, VisualSource source) {

    private static final VisualRendering NONE = new VisualRendering("", VisualSource.NONE);

    public static VisualRendering none() {
        return NONE;
    }

    public static VisualRendering generated(String url) {
        return new VisualRendering(url, VisualSource.AI_GENERATED);
    }

    public static VisualRendering localSvg(String dataUrl) {
        return new VisualRendering(dataUrl, VisualSource.LOCAL_SVG_FALLBACK);
    }

    public boolean isPresent() {
        return url != null && !url.isBlank();
    }
}
