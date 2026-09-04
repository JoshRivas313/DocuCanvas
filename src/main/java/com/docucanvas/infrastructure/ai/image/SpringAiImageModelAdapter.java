package com.docucanvas.infrastructure.ai.image;

import com.docucanvas.application.port.out.GenerativeImagePort;
import com.docucanvas.infrastructure.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador sobre {@link ImageModel} de Spring AI: genera una imagen real a
 * partir del prompt visual que el LLM derivó del contexto documental.
 *
 * <p><b>Por qué {@link ObjectProvider} y no {@code @ConditionalOnBean}:</b> la
 * condición parece la herramienta natural — "regístrate solo si hay un
 * proveedor de imágenes" — pero {@code @ConditionalOnBean} solo es fiable en
 * clases de autoconfiguración. Las condiciones se evalúan en el orden en que se
 * registran las definiciones de bean, y un {@code @Component} escaneado se
 * registra <em>antes</em> que las autoconfiguraciones que aportan el
 * {@code ImageModel}: en el momento de evaluar la condición, el bean del
 * proveedor todavía no existe, así que el adaptador no se registraría nunca —
 * ni siquiera con el starter presente. Es un fallo silencioso: la aplicación
 * arranca perfectamente y degrada siempre al SVG, dando la impresión de que el
 * proveedor no funciona.
 *
 * <p>{@code ObjectProvider} resuelve la dependencia de forma perezosa, en el
 * momento de usarla, cuando el contexto ya está completo. El adaptador existe
 * siempre y declara su disponibilidad real.
 *
 * <p>El código no menciona ningún proveedor concreto. Cambiar de DALL·E a
 * Stability AI, o a Imagen cuando Spring AI lo soporte, es cambiar una
 * dependencia y unas propiedades: esta clase no se toca. Es la misma promesa que
 * {@code ChatOptions} cumple para el texto.
 */
@Component
public class SpringAiImageModelAdapter implements GenerativeImagePort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiImageModelAdapter.class);

    private final ObjectProvider<ImageModel> imageModelProvider;
    private final RagProperties.Visual config;

    public SpringAiImageModelAdapter(ObjectProvider<ImageModel> imageModelProvider,
                                     RagProperties ragProperties) {
        this.imageModelProvider = imageModelProvider;
        this.config = ragProperties.visual();
    }

    @Override
    public boolean isAvailable() {
        return imageModelProvider.getIfAvailable() != null;
    }

    @Override
    public Optional<String> generate(String visualPrompt) {
        ImageModel imageModel = imageModelProvider.getIfAvailable();
        if (imageModel == null) {
            return Optional.empty();
        }

        log.info("Generando imagen con IA para el prompt visual: {}", visualPrompt);

        ImagePrompt prompt = new ImagePrompt(visualPrompt,
                ImageOptionsBuilder.builder()
                        .width(config.width())
                        .height(config.height())
                        .N(1)
                        .build());

        ImageResponse response = imageModel.call(prompt);
        if (response == null || response.getResult() == null) {
            return Optional.empty();
        }

        Image image = response.getResult().getOutput();
        if (image == null) {
            return Optional.empty();
        }

        // Los proveedores devuelven o bien una URL temporal, o bien el contenido
        // en base64. Se normalizan las dos formas a algo que un <img src> pueda
        // consumir directamente, para que el frontend no tenga que distinguirlas.
        if (image.getUrl() != null && !image.getUrl().isBlank()) {
            return Optional.of(image.getUrl());
        }
        if (image.getB64Json() != null && !image.getB64Json().isBlank()) {
            return Optional.of("data:image/png;base64," + image.getB64Json());
        }
        return Optional.empty();
    }

    @Override
    public String providerName() {
        ImageModel imageModel = imageModelProvider.getIfAvailable();
        return imageModel != null ? imageModel.getClass().getSimpleName() : "ninguno";
    }
}
