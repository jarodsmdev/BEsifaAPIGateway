package com.evecta.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import com.evecta.gateway.service.TokenValidationService;
import com.evecta.gateway.util.JwtUtil;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

@Component
@Order(-100)
public class JwtAuthenticationFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final TokenValidationService tokenValidationService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, TokenValidationService tokenValidationService) {
        this.jwtUtil = jwtUtil;
        this.tokenValidationService = tokenValidationService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (request.getMethod().name().equals("OPTIONS")) {
            log.info("[+] Preflight CORS permitido: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return exchange.getResponse().setComplete();
        }

        // Rutas públicas (no requieren token)
        if (path.startsWith("/auth/api/v1/")) {
            log.info("[+] Ruta pública (auth): {}", path);
            return chain.filter(exchange);
        }

        // Rutas protegidas - Validar token LOCALMENTE
        log.info("[!] Ruta protegida: {}", path);

        String authHeader = request.getHeaders().getFirst(AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[-] Token no proporcionado");
            return unauthorizedResponse(exchange, "Token no proporcionado");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!jwtUtil.validateToken(token)) {
            log.warn("[-] Token inválido o expirado localmente (firma/expiración)");
            return unauthorizedResponse(exchange, "Token inválido o expirado");
        }

        return tokenValidationService.validateToken(token)
                .flatMap(validationResult -> {
                    if (!"valid".equals(validationResult)) {
                        log.warn("[-] Token revocado o no válido en base de datos: {}", validationResult);
                        
                        String friendlyMessage = "Token inválido o no proporcionado";
                        if ("Token ha sido revocado".equals(validationResult)) {
                            friendlyMessage = "Se ha iniciado sesión en otro dispositivo o su sesión ha sido invalidada.";
                        } else if ("Token ha expirado".equals(validationResult)) {
                            friendlyMessage = "Su sesión ha expirado.";
                        } else if (validationResult != null && validationResult.contains("conexión")) {
                            friendlyMessage = "Error de conexión con el servicio de autenticación.";
                        }
                        
                        return unauthorizedResponse(exchange, friendlyMessage);
                    }

                    String username = jwtUtil.extractUsername(token);
                    List<String> rolesList = jwtUtil.extractRoles(token);
                    String rolesString = rolesList != null ? String.join(",", rolesList) : "";
                    log.info("[+] Token válido. Usuario: {} | Roles: {}", username, rolesString);

                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header(AUTH_HEADER, authHeader)
                            .header("X-Auth-User", username)
                            .header("X-Auth-Roles", rolesString)
                            .header("X-Auth-Token-Valid", "true")
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    @SuppressWarnings("null")
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();

        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        // response.getHeaders().set("Access-Control-Allow-Origin",
        // "http://127.0.0.1:3000");
        response.getHeaders().set("Content-Type", "application/json");

        String body = String.format("{\"error\":\"No autorizado\",\"message\":\"%s\"}", message);

        return response.writeWith(
                Objects.requireNonNull(Mono.just(Objects
                        .requireNonNull(response.bufferFactory().wrap(Objects.requireNonNull(body.getBytes()))))));
    }
}