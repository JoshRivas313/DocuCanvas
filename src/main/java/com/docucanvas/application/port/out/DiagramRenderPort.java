package com.docucanvas.application.port.out;

/**
 * Render local de un diagrama, sin llamar a ningún servicio externo.
 *
 * <p>Es el suelo del pipeline visual: siempre hay una implementación disponible,
 * no depende de red, credenciales ni GPU, y no puede agotar ninguna cuota. Por
 * eso es el destino de la degradación cuando {@link GenerativeImagePort} no
 * existe o falla.
 *
 * <p><b>Recibe el {@code visualPrompt} igual que el camino de IA.</b> Antes solo
 * recibía la pregunta y el contexto crudo, así que el respaldo elegía plantilla
 * por frecuencia de palabras del documento: el prompt que el LLM había derivado
 * del contexto se descartaba en cuanto no había proveedor de imagen. Ahora los
 * dos caminos parten de la misma interpretación del modelo; lo que cambia entre
 * ellos es la fidelidad del resultado, no su origen.
 */
public interface DiagramRenderPort {

    /**
     * @param visualPrompt prompt derivado por el LLM del contexto recuperado;
     *                     {@code null} si el modelo no produjo salida estructurada
     * @param question     pregunta original del usuario, usada como título
     * @param context      contexto documental recuperado, respaldo cuando no hay
     *                     {@code visualPrompt}
     * @return Data URL {@code data:image/svg+xml;base64,...}
     */
    String renderDiagram(String visualPrompt, String question, String context);
}
