package com.docucanvas.infrastructure.ai.image.gemini;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Registra el {@link ImageModel} de Gemini cuando el perfil lo activa.
 *
 * <p>Es una {@code @Configuration} con {@code @ConditionalOnProperty}, no un
 * {@code @Component} con {@code @ConditionalOnBean}: la condición depende solo de
 * una propiedad, no del orden en que Spring registre otros beans, así que aquí
 * sí es fiable.
 *
 * <p>{@code @ConditionalOnMissingBean(ImageModel.class)} cede el paso si ya hay
 * otro proveedor (p.ej. el perfil {@code imagegen}), para que activar los dos a
 * la vez no rompa el arranque por ambigüedad.
 */
@Configuration
@ConditionalOnProperty(prefix = "docucanvas.gemini.image", name = "enabled", havingValue = "true")
public class GeminiImageConfig {

    private static final Logger log = LoggerFactory.getLogger(GeminiImageConfig.class);

    @Bean
    @ConditionalOnMissingBean(ImageModel.class)
    public ImageModel geminiImageModel(GeminiImageProperties properties,
                                       RestClient.Builder restClientBuilder) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "docucanvas.gemini.image.api-key es obligatorio con la generacion de "
                            + "imagenes activa. Define GEMINI_API_KEY.");
        }
        log.info("[IMAGE] Gemini Developer API activa para imagenes: modelo={}", properties.model());
        return new GeminiImageModel(properties,
                restClientBuilder.requestFactory(timeoutFactory(properties.timeoutSeconds())));
    }

    /**
     * Sin timeout explícito, una llamada colgada al proveedor bloquea el hilo de
     * la petición HTTP indefinidamente: el render visual no está cubierto por el
     * future con timeout que sí protege la generación de texto.
     *
     * <p>Se configura aquí y no dentro del modelo a propósito. El timeout es una
     * preocupación de transporte, no del modelo; y fijar el {@code requestFactory}
     * dentro de la clase sobrescribiría el de {@code MockRestServiceServer},
     * dejando que los tests unitarios salieran a la red real.
     */
    private static SimpleClientHttpRequestFactory timeoutFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return factory;
    }

}
