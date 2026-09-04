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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador sobre {@link ImageModel} de Spring AI: genera una imagen real a
 * partir del prompt visual que el LLM derivó del contexto documental.
 *
 * <p><b>Por qué es condicional:</b> {@code ImageModel} es una interfaz del núcleo
 * de Spring AI, siempre presente en el classpath, pero solo existe un bean que
 * la implemente si se añade un starter de proveedor (OpenAI, Stability AI,
 * Azure…). La anotación {@link ConditionalOnBean} hace que este adaptador
 * aparezca únicamente en ese caso; sin proveedor configurado, el
 * {@code Optional<GenerativeImagePort>} del servicio llega vacío y el pipeline
 * cae al diagrama local sin que nada falle al arrancar.
 *
 * <p>El código no menciona ningún proveedor concreto. Cambiar de DALL·E a
 * Stability AI, o a Imagen cuando Spring AI lo soporte, es cambiar una
 * dependencia y unas propiedades: esta clase no se toca. Es la misma promesa que
 * {@code ChatOptions} cumple para el texto.
 */
@Component
@ConditionalOnBean(ImageModel.class)
public class SpringAiImageModelAdapter implements GenerativeImagePort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiImageModelAdapter.class);

    private final ImageModel imageModel;
    private final RagProperties.Visual config;

    public SpringAiImageModelAdapter(ImageModel imageModel, RagProperties ragProperties) {
        this.imageModel = imageModel;
        this.config = ragProperties.visual();
        log.info("Generación de imágenes por IA ACTIVA vía {}", providerName());
    }

    @Override
    public Optional<String> generate(String visualPrompt) {
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
        return imageModel.getClass().getSimpleName();
    }
}
