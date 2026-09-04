package com.docucanvas.application.port.out;

import java.util.Optional;

/**
 * Generación de imágenes mediante un modelo de IA.
 *
 * <p><b>Puede no existir en tiempo de ejecución.</b> Es el único puerto del
 * proyecto declarado como opcional a propósito: generar imágenes requiere un
 * proveedor externo con credenciales y coste por llamada, y el sistema tiene que
 * seguir funcionando sin él. Los consumidores lo inyectan como
 * {@code Optional<GenerativeImagePort>} y degradan a {@link DiagramRenderPort}
 * cuando no hay ninguna implementación disponible.
 */
public interface GenerativeImagePort {

    /**
     * @param visualPrompt descripción visual derivada del contexto documental
     * @return URL o Data URL de la imagen generada, o vacío si el proveedor no
     *         devolvió nada utilizable
     */
    Optional<String> generate(String visualPrompt);

    /** Nombre del proveedor, para trazas y para el panel de transparencia. */
    String providerName();
}
