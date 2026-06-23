package com.evecta.gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TokenValidationService {
    private static final Logger log = LoggerFactory.getLogger(TokenValidationService.class);

    private final WebClient webClient;

    public TokenValidationService(WebClient.Builder webClientBuilder,
            @Value("${AUTH_SERVICE_URL:http://auth-api:8080}") String authServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
    }

    public Mono<String> validateToken(String token) {
        return webClient.get()
                .uri("/auth/api/v1/validate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Boolean valid = (Boolean) response.get("valid");
                    if (valid != null && valid) {
                        log.info("[+] Token válido según auth-service");
                        return "valid";
                    }
                    String error = (String) response.get("error");
                    log.warn("[-] Token inválido según auth-service: {}", error);
                    return error != null ? error : "Token inválido";
                })
                .onErrorResume(e -> {
                    log.error("[-] Error al validar token con auth-service: {}", e.getMessage());
                    return Mono.just("Error de conexión con servicio de autenticación");
                });
    }
}