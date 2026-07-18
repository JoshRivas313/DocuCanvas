package com.docucanvas.infrastructure.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

/**
 * Rate limiting por IP para los endpoints computacionalmente costosos (invocan
 * al LLM o procesan archivos). Implementa una ventana fija con token-bucket en
 * memoria, sin dependencias externas.
 *
 * <p>Mitiga el abuso trivial de {@code /ask} y {@code /upload} como amplificadores
 * de coste (DoS). Los límites son configurables vía
 * {@code docucanvas.ratelimit.*}.
 *
 * <p><b>Nota:</b> el estado es por instancia (in-memory). En despliegues con
 * varias réplicas conviene un limitador distribuido (Redis/Bucket4j).
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/questions/ask",
            "/api/v1/documents/upload",
            "/api/v1/documents/import-text");

    private final int capacity;
    private final long windowMillis;
    // expireAfterAccess evita que una IP inactiva ocupe memoria indefinidamente
    // (el ConcurrentHashMap original nunca purgaba entradas).
    private final Cache<String, Bucket> buckets;

    public RateLimitingFilter(
            @Value("${docucanvas.ratelimit.capacity:20}") int capacity,
            @Value("${docucanvas.ratelimit.window-seconds:60}") long windowSeconds) {
        this.capacity = capacity;
        this.windowMillis = windowSeconds * 1000L;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofSeconds(windowSeconds * 2))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && LIMITED_PATHS.contains(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String key = request.getRemoteAddr();
        Bucket bucket = buckets.get(key, k -> new Bucket(capacity, windowMillis));

        if (bucket.tryConsume()) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit excedido para IP {} en {}", key, request.getRequestURI());
            response.setStatus(429); // 429 Too Many Requests (no hay constante en la API Servlet)
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"type\":\"https://docucanvas.io/errors/rate-limit\","
                    + "\"title\":\"Demasiadas peticiones\","
                    + "\"status\":429,"
                    + "\"detail\":\"Has superado el límite de peticiones. Inténtalo de nuevo en unos segundos.\"}");
        }
    }

    /** Token-bucket de ventana fija: {@code capacity} tokens por {@code windowMillis}. */
    private static final class Bucket {
        private final int capacity;
        private final long windowMillis;
        private int tokens;
        private long windowStart;

        Bucket(int capacity, long windowMillis) {
            this.capacity = capacity;
            this.windowMillis = windowMillis;
            this.tokens = capacity;
            this.windowStart = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMillis) {
                tokens = capacity;
                windowStart = now;
            }
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }
    }
}
