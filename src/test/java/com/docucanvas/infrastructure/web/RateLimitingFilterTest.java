package com.docucanvas.infrastructure.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tests de {@link RateLimitingFilter}: límite por IP, respuesta 429 y rutas exentas. */
@DisplayName("RateLimitingFilter — Límite por IP en endpoints costosos")
class RateLimitingFilterTest {

    private MockHttpServletRequest post(String uri, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    @DisplayName("Debe permitir hasta 'capacity' peticiones y devolver 429 en la siguiente")
    void bloqueaTrasAgotarCapacidad() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(2, 60);
        FilterChain chain = mock(FilterChain.class);

        // 2 permitidas
        for (int i = 0; i < 2; i++) {
            filter.doFilter(post("/api/v1/questions/ask", "10.0.0.1"), new MockHttpServletResponse(), chain);
        }
        // 3ª bloqueada
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/questions/ask", "10.0.0.1"), blocked, chain);

        verify(chain, times(2)).doFilter(any(), any());
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentType()).isEqualTo("application/problem+json");
        assertThat(blocked.getContentAsString()).contains("Demasiadas peticiones");
    }

    @Test
    @DisplayName("El límite es por IP: una IP agotada no afecta a otra")
    void limitePorIpIndependiente() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(1, 60);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(post("/api/v1/documents/upload", "1.1.1.1"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse blockedA = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/documents/upload", "1.1.1.1"), blockedA, chain);
        // Otra IP: su primer token sigue disponible
        MockHttpServletResponse okB = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/documents/upload", "2.2.2.2"), okB, chain);

        assertThat(blockedA.getStatus()).isEqualTo(429);
        assertThat(okB.getStatus()).isEqualTo(200);
        verify(chain, times(2)).doFilter(any(), any()); // la 1ª de A y la de B
    }

    @Test
    @DisplayName("Rutas no limitadas y métodos GET quedan exentos (shouldNotFilter)")
    void rutasExentas() {
        RateLimitingFilter filter = new RateLimitingFilter(1, 60);

        // GET a un endpoint limitado → exento
        MockHttpServletRequest getAsk = new MockHttpServletRequest("GET", "/api/v1/questions/ask");
        assertThat(filter.shouldNotFilter(getAsk)).isTrue();

        // POST a un endpoint NO limitado → exento
        MockHttpServletRequest postOther = new MockHttpServletRequest("POST", "/api/v1/documents");
        assertThat(filter.shouldNotFilter(postOther)).isTrue();

        // POST a un endpoint limitado → NO exento
        MockHttpServletRequest postAsk = new MockHttpServletRequest("POST", "/api/v1/questions/ask");
        assertThat(filter.shouldNotFilter(postAsk)).isFalse();
    }
}
