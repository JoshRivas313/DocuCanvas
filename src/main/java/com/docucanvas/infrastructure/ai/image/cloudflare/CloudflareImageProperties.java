package com.docucanvas.infrastructure.ai.image.cloudflare;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Generación de imágenes con Workers AI de Cloudflare.
 *
 * <p>Existe por una razón de coste, no de arquitectura: la Gemini Developer API
 * no ofrece nivel gratuito para imagen (responde 429 con {@code limit: 0}),
 * mientras que Workers AI incluye 10.000 Neurons diarios sin coste. Una imagen
 * de 1024x1024 con FLUX-1-schnell consume unos 57 Neurons — cuatro teselas de
 * 512x512 a 4,8 mas cuatro pasos a 9,6 — asi que la asignacion diaria cubre
 * alrededor de 170 imagenes y se renueva cada dia.
 *
 * @param enabled   activa este proveedor
 * @param accountId identificador de cuenta de Cloudflare
 * @param apiToken  token de API con permiso de Workers AI
 * @param model     modelo de Workers AI
 * @param steps     pasos de difusion (FLUX schnell admite hasta 8; 4 es su valor por defecto)
 * @param timeoutSeconds cota de espera de la llamada; sin ella una respuesta
 *                       colgada bloquea el hilo de la peticion HTTP
 */
@ConfigurationProperties(prefix = "docucanvas.cloudflare.image")
public record CloudflareImageProperties(
        @DefaultValue("false") boolean enabled,
        String accountId,
        String apiToken,
        @DefaultValue("@cf/black-forest-labs/flux-1-schnell") String model,
        @DefaultValue("4") int steps,
        @DefaultValue("60") int timeoutSeconds) {
}
