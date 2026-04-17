package com.evecta.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                // Deshabilitar CSRF (API stateless)
                .csrf(csrf -> csrf.disable())

                // Deshabilitar autenticación básica HTTP
                .httpBasic(httpBasic -> httpBasic.disable())

                // Deshabilitar formulario de login por defecto
                .formLogin(formLogin -> formLogin.disable())

                // Deshabilitar logout por defecto
                .logout(logout -> logout.disable())

                // [!] IMPORTANTE: Deshabilitar la autenticación reactiva por defecto
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .cors(cors -> {})
                // Configurar autorización
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()  // JwtAuthenticationFilter maneja la seguridad
                )
                .build();
    }
}