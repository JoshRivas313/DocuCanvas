package com.docucanvas.application.visual;

/**
 * Origen real de la representación visual devuelta al usuario.
 *
 * <p>Se expone en la API a propósito. Un sistema que dice "esta imagen la generó
 * una IA" cuando en realidad la dibujó una plantilla local está mintiendo, y en
 * una demo técnica el público tiene todo el derecho a saber qué camino se tomó.
 * Este enum convierte esa distinción en un dato verificable en vez de una
 * afirmación del presentador.
 */
public enum VisualSource {

    /** Imagen generada por un modelo de difusión vía {@code ImageModel} de Spring AI. */
    GENERATIVE_IMAGE_MODEL,

    /** Diagrama SVG construido localmente a partir de plantillas y del contexto. */
    LOCAL_SVG_FALLBACK,

    /** No se pudo producir ninguna representación. */
    NONE
}
