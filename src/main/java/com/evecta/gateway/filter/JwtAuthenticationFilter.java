package com.evecta.gateway.filter;

import lombok.AllArgsConstructor;
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
import com.evecta.gateway.util.JwtUtil;
import reactor.core.publisher.Mono;

@Component
@Order(-100)
@AllArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Rutas públicas (no requieren token)
        if (path.startsWith("/auth/login") || path.startsWith("/auth/register")) {
            log.info("[+] Ruta pública: {}", path);
            return chain.filter(exchange);
        }

        // Rutas protegidas - Validar token LOCALMENTE
        log.info("[!] Ruta protegida: {}", path);

        String authHeader = request.getHeaders().getFirst(AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[-] Token no proporcionado");
            return unauthorizedResponse(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!jwtUtil.validateToken(token)) {
            log.warn("[-] Token inválido");
            return unauthorizedResponse(exchange);
        }

        String username = jwtUtil.extractUsername(token);
        log.info("[+] Token válido para: {}", username);

        // [!] Mantener el token original Y agregar el usuario
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(AUTH_HEADER, authHeader)        // Mantiene el token original
                .header("X-Auth-User", username)        // Agrega usuario para logging
                .header("X-Auth-Token-Valid", "true")   // Indica que el token es válido
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        String body = "{\"error\": \"No autorizado\", \"message\": \"Token inválido o no proporcionado\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }
}