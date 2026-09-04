package com.docucanvas.application.port.out;

import java.util.Optional;

/**
 * Generación de imágenes mediante un modelo de IA.
 *
 * <p><b>Puede no haber proveedor en tiempo de ejecución.</b> Generar imágenes
 * requiere un servicio externo con credenciales y coste por llamada, y el
 * sistema tiene que seguir funcionando sin él. Esa condición se expresa con
 * {@link #isAvailable()} en vez de con la ausencia del bean: un puerto que a
 * veces existe y a veces no obliga a cada consumidor a manejar la ausencia, y
 * —lo que es peor— a depender de en qué orden registra Spring los beans.
 */
public interface GenerativeImagePort {

    /**
     * ¿Hay un proveedor de generación de imágenes configurado y utilizable?
     *
     * <p>Se consulta en cada petición, no una sola vez al arrancar: el proveedor
     * lo aporta una autoconfiguración que puede resolverse después de que este
     * componente se construya.
     */
    boolean isAvailable();

    /**
     * @param visualPrompt descripción visual derivada del contexto documental
     * @return URL o Data URL de la imagen generada, o vacío si no hay proveedor
     *         o no devolvió nada utilizable
     */
    Optional<String> generate(String visualPrompt);

    /** Nombre del proveedor, para trazas y para el panel de transparencia. */
    String providerName();
}
