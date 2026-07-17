package com.docucanvas.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Seguridad HTTP dependiente del perfil.
 *
 * <ul>
 *   <li><b>dev / test / por defecto</b> ({@code @Profile("!prod")}): acceso abierto,
 *       pensado para desarrollo local y la demo.</li>
 *   <li><b>prod</b> ({@code @Profile("prod")}): añade cabeceras de seguridad (HSTS,
 *       Referrer-Policy, nosniff) y deja el punto de extensión para exigir
 *       autenticación.</li>
 * </ul>
 *
 * <p>El rate limiting ({@link com.docucanvas.infrastructure.web.RateLimitingFilter})
 * actúa en todos los perfiles.
 *
 * <p><b>Para habilitar autenticación en producción</b> (paso siguiente, requiere
 * añadir un flujo de login al frontend Thymeleaf): sustituir {@code permitAll()}
 * por reglas {@code authenticated()}, activar CSRF con
 * {@code CookieCsrfTokenRepository} y que el cliente fetch envíe el header
 * {@code X-XSRF-TOKEN}.
 */
@Configuration
public class SecurityConfig {

    /** Perfil de desarrollo / demo: acceso abierto. */
    @Bean
    @Profile("!prod")
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /**
     * Perfil de producción: cabeceras de seguridad endurecidas. El acceso sigue
     * abierto porque exigir autenticación requiere primero el flujo de login del
     * frontend (ver javadoc de la clase); este es el punto de extensión previsto.
     */
    @Bean
    @Profile("prod")
    public SecurityFilterChain prodFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000))
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }
}
