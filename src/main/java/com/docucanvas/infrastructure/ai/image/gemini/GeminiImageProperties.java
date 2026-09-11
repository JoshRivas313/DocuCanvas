package com.docucanvas.infrastructure.ai.image.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de la generación de imágenes con la Gemini Developer API.
 *
 * @param enabled activa el {@code ImageModel}. Falso por defecto: sin clave no
 *                debe existir el bean, y el pipeline degrada al SVG local
 * @param apiKey  clave de Google AI Studio. La misma que usa el chat
 * @param model   modelo de imagen de la Developer API
 * @param timeoutSeconds tiempo máximo de espera de la llamada
 */
@ConfigurationProperties(prefix = "docucanvas.gemini.image")
public record GeminiImageProperties(
        @DefaultValue("false") boolean enabled,
        String apiKey,
        @DefaultValue("gemini-2.5-flash-image") String model,
        @DefaultValue("90") int timeoutSeconds) {
}
