package com.docucanvas.infrastructure.ai.image.cloudflare;

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

/** Registra el {@link ImageModel} de Cloudflare cuando la configuracion lo activa. */
@Configuration
@ConditionalOnProperty(prefix = "docucanvas.cloudflare.image", name = "enabled", havingValue = "true")
public class CloudflareImageConfig {

    private static final Logger log = LoggerFactory.getLogger(CloudflareImageConfig.class);

    @Bean
    @ConditionalOnMissingBean(ImageModel.class)
    public ImageModel cloudflareImageModel(CloudflareImageProperties properties,
                                           RestClient.Builder restClientBuilder) {
        if (properties.accountId() == null || properties.accountId().isBlank()
                || properties.apiToken() == null || properties.apiToken().isBlank()) {
            throw new IllegalStateException(
                    "docucanvas.cloudflare.image requiere account-id y api-token. "
                            + "Define CLOUDFLARE_ACCOUNT_ID y CLOUDFLARE_API_TOKEN.");
        }
        log.info("[IMAGE] Cloudflare Workers AI activo para imagenes: modelo={}", properties.model());
        return new CloudflareImageModel(properties,
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
