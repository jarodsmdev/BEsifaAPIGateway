package com.evecta.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@Slf4j
public class TokenValidationService {

    private final WebClient webClient;

    public TokenValidationService(WebClient.Builder webClientBuilder,
            @Value("${AUTH_SERVICE_URL:http://auth-api:8080}") String authServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
    }

    public Mono<Boolean> isTokenValid(String token) {
        return webClient.get()
                .uri("/auth/api/v1/validate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Boolean valid = (Boolean) response.get("valid");
                    if (valid != null && valid) {
                        log.info("[+] Token válido según auth-service");
                        return true;
                    }
                    String error = (String) response.get("error");
                    log.warn("[-] Token inválido según auth-service: {}", error);
                    return false;
                })
                .onErrorResume(e -> {
                    log.error("[-] Error al validar token con auth-service: {}", e.getMessage());
                    return Mono.just(false);
                });
    }
}