package com.docucanvas.application.port.out;

/**
 * Render local de un diagrama a partir del contexto documental, sin llamar a
 * ningún servicio externo.
 *
 * <p>Es el suelo del pipeline visual: siempre hay una implementación disponible,
 * no depende de red, credenciales ni GPU, y no puede agotar ninguna cuota. Por
 * eso es el destino de la degradación cuando {@link GenerativeImagePort} no
 * existe o falla.
 */
public interface DiagramRenderPort {

    /**
     * @param question pregunta original del usuario, usada como título
     * @param context  contexto documental recuperado
     * @return Data URL {@code data:image/svg+xml;base64,...}
     */
    String renderDiagram(String question, String context);
}
