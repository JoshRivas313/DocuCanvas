package com.docucanvas.infrastructure.ai.image;

import com.docucanvas.application.port.out.DiagramRenderPort;
import com.docucanvas.application.service.ImageGenerationService;
import org.springframework.stereotype.Component;

/**
 * Respaldo local: delega en el generador de diagramas SVG por plantillas.
 *
 * <p>Este adaptador es lo que convierte al generador SVG en una pieza
 * arquitectónica honesta. Sigue haciendo exactamente lo mismo que en la versión
 * base — detectar un tema por palabras clave y rellenar una plantilla — pero
 * ahora ocupa el lugar que le corresponde: detrás de un puerto llamado
 * {@link DiagramRenderPort}, no detrás de uno que prometa generación por IA.
 *
 * <p>Sus virtudes reales siguen intactas y son las que lo hacen buen respaldo:
 * es determinista, no necesita red ni credenciales, tarda milisegundos y no
 * puede agotar ninguna cuota en mitad de una demo.
 */
@Component
public class SvgDiagramAdapter implements DiagramRenderPort {

    private final ImageGenerationService imageGenerationService;

    public SvgDiagramAdapter(ImageGenerationService imageGenerationService) {
        this.imageGenerationService = imageGenerationService;
    }

    @Override
    public String renderDiagram(String question, String context) {
        return imageGenerationService.generateImageDataUrl(question, context);
    }
}
