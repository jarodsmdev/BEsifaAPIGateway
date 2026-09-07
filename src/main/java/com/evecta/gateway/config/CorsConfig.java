package com.evecta.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;

import java.util.List;

/**
 * Configuración CORS del API Gateway.
 * 
 * allowCredentials(true) es REQUERIDO para que las cookies HttpOnly
 * se envíen en peticiones cross-origin. Cuando se usan credenciales,
 * no se puede usar "*" como origen: se lista explícitamente cada origen.
 */
@Configuration
public class CorsConfig {

        @Bean
        public CorsWebFilter corsWebFilter() {

                CorsConfiguration config = new CorsConfiguration();

                // Orígenes permitidos (no se puede usar "*" con allowCredentials=true)
                config.setAllowedOrigins(List.of(
                                "http://localhost:5173",
                                "https://sifacore.netlify.app",
                                "https://sifacore2.netlify.app",
                                "http://sifacore.s3-website-us-east-1.amazonaws.com",
                                "https://sifacore.s3.us-east-1.amazonaws.com"
                        ));

                config.setAllowedMethods(List.of(
                                "GET",
                                "POST",
                                "PUT",
                                "DELETE",
                                "PATCH",
                                "OPTIONS"));

                config.setAllowedHeaders(List.of(
                                "Authorization",
                                "Content-Type",
                                "X-Requested-With",
                                "X-Client-Origin"));

                // Permitir credenciales (cookies HttpOnly) en peticiones cross-origin
                config.setAllowCredentials(true);

                // Exponer Set-Cookie para que el navegador procese las cookies de sesión
                config.setExposedHeaders(List.of("Set-Cookie"));

                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

                source.registerCorsConfiguration("/**", config);

                return new CorsWebFilter(source);
        }
}