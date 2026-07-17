package com.docucanvas.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuración de caché con TTL para la visualización de chunks.
 *
 * <p>Sustituye el {@code ConcurrentMapCacheManager} por defecto (sin expiración)
 * por Caffeine con expiración por escritura, evitando que la proyección PCA/cluster
 * quede cacheada indefinidamente. La invalidación explícita sigue ocurriendo vía
 * {@code @CacheEvict} tras cada ingesta.
 */
@Configuration
public class CacheConfig {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final long MAX_ENTRIES = 100;

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("chunksGlobal", "chunksDoc");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_ENTRIES));
        return manager;
    }
}
