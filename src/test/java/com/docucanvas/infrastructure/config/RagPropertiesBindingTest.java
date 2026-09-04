package com.docucanvas.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que {@link RagProperties} se enlaza correctamente desde
 * configuración y que sus valores por defecto son los que el proyecto espera.
 *
 * <p>Usa {@link ApplicationContextRunner} en lugar de {@code @SpringBootTest}: no
 * necesita base de datos, ni Docker, ni un modelo de IA levantado. Probar el
 * enlace de configuración no debería requerir arrancar la aplicación entera.
 */
@DisplayName("RagProperties — Enlace de configuración")
class RagPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(EnableRagProperties.class);

    @org.springframework.boot.context.properties.EnableConfigurationProperties(RagProperties.class)
    static class EnableRagProperties {}

    @Test
    @DisplayName("Sin configuración explícita aplica los valores por defecto de la versión base")
    void valoresPorDefectoReproducenLaVersionBase() {
        runner.run(context -> {
            RagProperties props = context.getBean(RagProperties.class);

            // Los cinco argumentos que estaban incrustados en el TokenTextSplitter.
            assertThat(props.chunking().chunkSize()).isEqualTo(200);
            assertThat(props.chunking().minChunkSizeChars()).isEqualTo(50);
            assertThat(props.chunking().minChunkLengthToEmbed()).isEqualTo(5);
            assertThat(props.chunking().maxNumChunks()).isEqualTo(10_000);
            assertThat(props.chunking().keepSeparator()).isTrue();

            // Umbrales que antes eran literales dentro del método de retrieval.
            assertThat(props.retrieval().defaultTopK()).isEqualTo(5);
            assertThat(props.retrieval().strictThreshold()).isEqualTo(0.3);
            assertThat(props.retrieval().relaxedThreshold()).isEqualTo(0.0);

            assertThat(props.generation().maxOutputTokens()).isEqualTo(400);
        });
    }

    @Test
    @DisplayName("Los valores se pueden sobreescribir por propiedad, sin recompilar")
    void sobreescrituraPorPropiedad() {
        runner.withPropertyValues(
                "docucanvas.rag.chunking.chunk-size=512",
                "docucanvas.rag.retrieval.max-top-k=8",
                "docucanvas.rag.generation.model=gemini-2.0-flash"
        ).run(context -> {
            RagProperties props = context.getBean(RagProperties.class);
            assertThat(props.chunking().chunkSize()).isEqualTo(512);
            assertThat(props.retrieval().maxTopK()).isEqualTo(8);
            assertThat(props.generation().model()).isEqualTo("gemini-2.0-flash");
            // Lo no sobreescrito conserva su valor por defecto.
            assertThat(props.retrieval().strictThreshold()).isEqualTo(0.3);
        });
    }
}
