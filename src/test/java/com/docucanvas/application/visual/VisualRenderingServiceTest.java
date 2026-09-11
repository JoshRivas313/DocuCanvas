package com.docucanvas.application.visual;

import com.docucanvas.application.port.out.DiagramRenderPort;
import com.docucanvas.application.port.out.GenerativeImagePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests del orquestador multimodal: cuándo se genera una imagen con IA, cuándo
 * se cae al diagrama local, y que el origen declarado corresponda con lo que
 * realmente ocurrió.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VisualRenderingService — Generación con IA y degradación al SVG local")
class VisualRenderingServiceTest {

    @Mock private GenerativeImagePort generativeImage;
    @Mock private DiagramRenderPort diagramRender;

    private static final String SVG = "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=";
    private static final String PROMPT = "clean vector infographic of a distributed architecture, no text";

    @Test
    @DisplayName("Con proveedor disponible y prompt visual, genera la imagen con IA")
    void conProveedorYPromptGeneraConIa() {
        when(generativeImage.isAvailable()).thenReturn(true);
        when(generativeImage.generate(PROMPT)).thenReturn(Optional.of("https://cdn.example/img.png"));
        VisualRenderingService service = new VisualRenderingService(generativeImage, diagramRender);

        VisualRendering result = service.render(PROMPT, "¿Qué arquitectura describe?", "contexto");

        assertThat(result.source()).isEqualTo(VisualSource.AI_GENERATED);
        assertThat(result.url()).isEqualTo("https://cdn.example/img.png");
        verifyNoInteractions(diagramRender);
    }

    @Test
    @DisplayName("Sin proveedor configurado usa el diagrama local y lo declara como respaldo")
    void sinProveedorUsaDiagramaLocal() {
        when(diagramRender.renderDiagram(any(), anyString(), anyString())).thenReturn(SVG);
        VisualRenderingService service = new VisualRenderingService(new NoGenerativeImageProvider(), diagramRender);

        VisualRendering result = service.render(PROMPT, "pregunta", "contexto");

        assertThat(result.source()).isEqualTo(VisualSource.LOCAL_SVG_FALLBACK);
        assertThat(result.url()).isEqualTo(SVG);
    }

    @Test
    @DisplayName("Si el proveedor de IA falla, la respuesta no se pierde: cae al diagrama local")
    void falloDelProveedorDegradaAlDiagramaLocal() {
        when(generativeImage.isAvailable()).thenReturn(true);
        when(generativeImage.generate(anyString()))
                .thenThrow(new RuntimeException("429 Too Many Requests"));
        when(diagramRender.renderDiagram(any(), anyString(), anyString())).thenReturn(SVG);
        VisualRenderingService service = new VisualRenderingService(generativeImage, diagramRender);

        VisualRendering result = service.render(PROMPT, "pregunta", "contexto");

        assertThat(result.source()).isEqualTo(VisualSource.LOCAL_SVG_FALLBACK);
        assertThat(result.url()).isEqualTo(SVG);
    }

    @Test
    @DisplayName("Si el proveedor responde vacío también se degrada, sin devolver una imagen en blanco")
    void respuestaVaciaDelProveedorDegrada() {
        when(generativeImage.isAvailable()).thenReturn(true);
        when(generativeImage.generate(anyString())).thenReturn(Optional.empty());
        when(diagramRender.renderDiagram(any(), anyString(), anyString())).thenReturn(SVG);
        VisualRenderingService service = new VisualRenderingService(generativeImage, diagramRender);

        VisualRendering result = service.render(PROMPT, "pregunta", "contexto");

        assertThat(result.source()).isEqualTo(VisualSource.LOCAL_SVG_FALLBACK);
    }

    @Test
    @DisplayName("Sin prompt visual no se llama al proveedor de pago: se va directo al respaldo")
    void sinPromptVisualNoLlamaAlProveedor() {
        when(diagramRender.renderDiagram(any(), anyString(), anyString())).thenReturn(SVG);
        VisualRenderingService service = new VisualRenderingService(generativeImage, diagramRender);

        VisualRendering result = service.render(null, "pregunta", "contexto");

        assertThat(result.source()).isEqualTo(VisualSource.LOCAL_SVG_FALLBACK);
        // Sin prompt no hay nada que generar: gastar una llamada sería tirar dinero.
        verify(generativeImage, never()).generate(anyString());
    }

    @Test
    @DisplayName("Si fallan los dos caminos devuelve NONE en vez de romper la respuesta")
    void falloTotalDevuelveNone() {
        when(generativeImage.isAvailable()).thenReturn(true);
        when(generativeImage.generate(anyString())).thenThrow(new RuntimeException("sin cuota"));
        when(diagramRender.renderDiagram(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("fallo de render"));
        VisualRenderingService service = new VisualRenderingService(generativeImage, diagramRender);

        VisualRendering result = service.render(PROMPT, "pregunta", "contexto");

        assertThat(result.source()).isEqualTo(VisualSource.NONE);
        assertThat(result.isPresent()).isFalse();
    }
}
