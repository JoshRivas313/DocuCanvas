package com.docucanvas.integration;

import com.docucanvas.application.port.out.GenerativeImagePort;
import com.docucanvas.infrastructure.config.RagProperties;
import com.docucanvas.infrastructure.ai.image.SpringAiImageModelAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba contra el proveedor REAL de generación de imágenes.
 *
 * <p><b>Se ejecuta solo si existe {@code OPENAI_API_KEY} en el entorno.</b> Sin
 * la variable, JUnit la marca como omitida en vez de fallar: una prueba que
 * requiere una credencial de pago no puede romper el build de quien no la tiene,
 * pero tampoco debe desaparecer del proyecto — es la única que demuestra que
 * {@code imageModel.call()} funciona contra un servicio de verdad y no contra un
 * stub.
 *
 * <p>Ejecutar con:
 * <pre>
 * export OPENAI_API_KEY=sk-...
 * ./mvnw -Pimagegen test -Dtest=RealImageProviderIT
 * </pre>
 *
 * <p>Requiere el perfil Maven {@code imagegen}, que es el que aporta las clases
 * del proveedor al classpath. Sin él, la clase no compila y el test tampoco se
 * incluye en el build por defecto.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@DisplayName("Integración real — ImageModel genera una imagen de verdad")
class RealImageProviderIT {

    private static final String VISUAL_PROMPT =
            "clean minimal vector infographic of a payment reconciliation service "
            + "reading events from a message queue, flat design, no text, no letters";

    @Test
    @DisplayName("El visualPrompt llega al proveedor y vuelve una imagen utilizable")
    void generaUnaImagenRealDesdeElVisualPrompt() {
        ImageModel imageModel = new OpenAiImageModel(
                OpenAiImageApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build());

        GenerativeImagePort port = new SpringAiImageModelAdapter(
                new SingletonObjectProvider<>(imageModel), properties());

        assertThat(port.isAvailable())
                .as("con el perfil imagegen y la API key debe haber proveedor")
                .isTrue();

        Optional<String> image = port.generate(VISUAL_PROMPT);

        assertThat(image).as("el proveedor real debe devolver una imagen").isPresent();
        // El adaptador normaliza ambas formas de respuesta a algo que un <img src>
        // pueda consumir: URL del proveedor o Data URL en base64.
        assertThat(image.get()).matches("^(https?://|data:image/).+");
    }

    private RagProperties properties() {
        return new RagProperties(
                new RagProperties.Chunking(200, 50, 5, 10000, true),
                new RagProperties.Retrieval(5, 20, 0.3, 0.0),
                new RagProperties.Generation("llama3.2", 0.7, 400, 3.7, 25.0, 5.0, 15, 30, 150),
                new RagProperties.Visual(1024, 1024));
    }

    /** ObjectProvider mínimo para construir el adaptador fuera del contexto de Spring. */
    private record SingletonObjectProvider<T>(T instance) implements ObjectProvider<T> {
        @Override public T getObject() { return instance; }
        @Override public T getObject(Object... args) { return instance; }
        @Override public T getIfAvailable() { return instance; }
        @Override public T getIfUnique() { return instance; }
    }
}
