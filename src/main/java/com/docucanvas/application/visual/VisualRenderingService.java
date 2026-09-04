package com.docucanvas.application.visual;

import com.docucanvas.application.port.out.DiagramRenderPort;
import com.docucanvas.application.port.out.GenerativeImagePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Decide y ejecuta cómo se representa visualmente una respuesta RAG.
 *
 * <p><b>El "plot twist" del proyecto, implementado de verdad.</b> La versión base
 * anunciaba generación de imágenes por IA pero lo que hacía era detectar un tema
 * por coincidencia de palabras clave y rellenar una de ocho plantillas SVG
 * escritas a mano. Aquí la jerarquía es la correcta:
 *
 * <ol>
 *   <li>Si hay un {@link GenerativeImagePort} disponible y el modelo produjo un
 *       prompt visual, se genera una imagen real con {@code ImageModel} de
 *       Spring AI a partir de ese prompt.</li>
 *   <li>Si no hay proveedor configurado, o la llamada falla, o el modelo no
 *       produjo prompt visual, se cae al diagrama SVG local.</li>
 * </ol>
 *
 * <p>El SVG deja así de ser <em>el</em> mecanismo para pasar a ser <em>el
 * respaldo</em> — que es lo que siempre fue en realidad. La degradación no es un
 * parche: en una demo en vivo es la diferencia entre una imagen peor y ninguna
 * imagen, y {@link VisualSource} deja constancia de cuál de los dos caminos se
 * tomó en cada respuesta.
 */
@Service
public class VisualRenderingService {

    private static final Logger log = LoggerFactory.getLogger(VisualRenderingService.class);

    private final Optional<GenerativeImagePort> generativeImage;
    private final DiagramRenderPort diagramRender;

    public VisualRenderingService(Optional<GenerativeImagePort> generativeImage,
                                  DiagramRenderPort diagramRender) {
        this.generativeImage = generativeImage;
        this.diagramRender = diagramRender;
        log.info("Render visual: generación por IA {}; respaldo local SVG activo",
                generativeImage.map(p -> "disponible (" + p.providerName() + ")")
                        .orElse("no configurada"));
    }

    /**
     * @param visualPrompt prompt derivado por el LLM del contexto; puede ser {@code null}
     *                     si el modelo no produjo salida estructurada
     * @param question     pregunta original, para el respaldo local
     * @param context      contexto recuperado, para el respaldo local
     */
    public VisualRendering render(String visualPrompt, String question, String context) {
        if (generativeImage.isPresent() && visualPrompt != null && !visualPrompt.isBlank()) {
            try {
                Optional<String> url = generativeImage.get().generate(visualPrompt);
                if (url.isPresent()) {
                    return VisualRendering.generated(url.get());
                }
                log.warn("El proveedor de imagen no devolvió contenido; se usa el respaldo local");
            } catch (Exception e) {
                // Un fallo del proveedor externo no puede tumbar la respuesta:
                // el usuario ya tiene su texto, la imagen es un extra.
                log.warn("Fallo generando la imagen con IA ({}); se usa el respaldo local",
                        e.getMessage());
            }
        }
        return renderFallback(question, context);
    }

    private VisualRendering renderFallback(String question, String context) {
        try {
            String dataUrl = diagramRender.renderDiagram(question, context);
            return dataUrl != null && !dataUrl.isBlank()
                    ? VisualRendering.localSvg(dataUrl)
                    : VisualRendering.none();
        } catch (Exception e) {
            log.error("Fallo también el render local del diagrama", e);
            return VisualRendering.none();
        }
    }
}
