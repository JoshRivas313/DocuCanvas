package com.docucanvas.infrastructure.ai.image;

import com.docucanvas.application.port.out.GenerativeImagePort;
import com.docucanvas.application.service.ConceptExtractor;
import com.docucanvas.application.service.ImageGenerationService;
import com.docucanvas.infrastructure.config.RagProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el comportamiento condicional del pipeline visual, que es donde vive
 * el riesgo real de esta arquitectura: si {@link ConditionalOnBean} no se evalúa
 * como se espera, la aplicación o bien no arranca sin proveedor, o bien no usa
 * el proveedor cuando sí lo hay. Ninguna de las dos cosas se detecta con un test
 * unitario normal.
 */
@DisplayName("Cableado del pipeline visual — carga condicional del proveedor")
class VisualAdapterWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ConceptExtractor.class)
            .withBean(ImageGenerationService.class)
            .withBean(RagProperties.class, VisualAdapterWiringTest::defaultProperties)
            .withUserConfiguration(SvgDiagramAdapter.class, SpringAiImageModelAdapter.class);

    static RagProperties defaultProperties() {
        return new RagProperties(
                new RagProperties.Chunking(200, 50, 5, 10000, true),
                new RagProperties.Retrieval(5, 20, 0.3, 0.0),
                new RagProperties.Generation("llama3.2", 0.7, 400, 3.7, 25.0, 5.0, 15, 30, 150),
                new RagProperties.Visual(1024, 1024));
    }

    @Test
    @DisplayName("Sin bean ImageModel la aplicación arranca y el proveedor se declara no disponible")
    void sinImageModelElProveedorNoEstaDisponible() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SvgDiagramAdapter.class);

            // El adaptador existe siempre; lo que cambia es su disponibilidad.
            // Registrarlo condicionalmente no funcionaria: @ConditionalOnBean sobre
            // un @Component se evalua antes de que las autoconfiguraciones aporten
            // el ImageModel, y el adaptador no se registraria nunca.
            GenerativeImagePort port = context.getBean(GenerativeImagePort.class);
            assertThat(port.isAvailable()).isFalse();
            assertThat(port.generate("un diagrama")).isEmpty();
        });
    }

    @Test
    @DisplayName("Con un bean ImageModel presente, el proveedor pasa a estar disponible")
    void conImageModelElProveedorEstaDisponible() {
        runner.withUserConfiguration(StubImageModelConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            GenerativeImagePort port = context.getBean(GenerativeImagePort.class);
            assertThat(port.isAvailable()).isTrue();
            assertThat(port.providerName()).isNotEqualTo("ninguno");
        });
    }

    @Test
    @DisplayName("El adaptador normaliza una respuesta en base64 a un Data URL utilizable por el navegador")
    void normalizaBase64ADataUrl() {
        runner.withUserConfiguration(StubImageModelConfig.class).run(context -> {
            GenerativeImagePort port = context.getBean(GenerativeImagePort.class);
            assertThat(port.generate("un diagrama"))
                    .contains("data:image/png;base64,QUJD");
        });
    }

    /** Simula el bean que aportaría un starter de proveedor (OpenAI, Stability…). */
    static class StubImageModelConfig {
        @Bean
        ImageModel imageModel() {
            return prompt -> new ImageResponse(
                    List.of(new ImageGeneration(new Image(null, "QUJD"))));
        }
    }
}
