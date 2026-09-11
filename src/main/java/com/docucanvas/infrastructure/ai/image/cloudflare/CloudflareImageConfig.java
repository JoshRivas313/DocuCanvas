package com.docucanvas.infrastructure.ai.image.cloudflare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

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
        return new CloudflareImageModel(properties, restClientBuilder);
    }
}
