package com.evecta.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpCookie;
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
    private static final String ACCESS_TOKEN_COOKIE = "access_token";

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

        if (path.startsWith("/swagger-ui") ||
                path.contains("/openapi.json") ||
                path.contains("/v3/api-docs") ||
                path.equals("/swagger-ui.html") ||
                path.startsWith("/webjars")) {

            log.info("[+] Acceso libre concedido a recursos de documentación: {}", path);
            return chain.filter(exchange);
        }

        // Rutas protegidas - Validar token LOCALMENTE
        log.info("[!] Ruta protegida: {}", path);

        // Obtener el token desde la cookie httpOnly (preferente) o header Authorization (fallback para apps móviles)
        TokenSource tokenSource = extractToken(request);

        if (tokenSource == null) {
            log.warn("[-] Token no proporcionado");
            return unauthorizedResponse(exchange, "Token no proporcionado");
        }

        final String token = tokenSource.token();

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

                    // Construir el header Authorization para reenviarlo a los servicios downstream.
                    // Si el token venía de una cookie, se construye "Bearer <token>".
                    // Si venía del header original, se conserva.
                    final String forwardedAuthHeader = tokenSource.authHeader() != null
                            ? tokenSource.authHeader()
                            : BEARER_PREFIX + token;

                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header(AUTH_HEADER, forwardedAuthHeader)
                            .header("X-Auth-User", username)
                            .header("X-Auth-Roles", rolesString)
                            .header("X-Auth-Token-Valid", "true")
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    /**
     * Record que encapsula el token extraído y el header Authorization original.
     * 
     * @param token      Token JWT extraído
     * @param authHeader Header Authorization original (null si el token vino de cookie)
     */
    private record TokenSource(String token, String authHeader) {
    }

    /**
     * Extrae el token JWT desde la cookie httpOnly o el header Authorization.
     * 
     * Preferencia de extracción:
     * 1. Cookie access_token (aplicaciones web con cookies HttpOnly)
     * 2. Header Authorization: Bearer <token> (fallback para apps móviles)
     * 
     * @param request La petición HTTP reactiva
     * @return TokenSource con el token y header, o null si no hay token
     */
    private TokenSource extractToken(ServerHttpRequest request) {
        // 1. Intentar con cookie httpOnly
        String token = getAccessTokenFromCookie(request);
        if (token != null) {
            return new TokenSource(token, null);
        }

        // 2. Fallback: header Authorization
        final String authHeader = request.getHeaders().getFirst(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return new TokenSource(authHeader.substring(BEARER_PREFIX.length()), authHeader);
        }

        return null;
    }

    /**
     * Extrae el access token JWT desde la cookie httpOnly.
     * 
     * En el API Gateway (WebFlux/Reactive), las cookies se leen
     * desde el ServerHttpRequest de forma reactiva.
     * 
     * @param request La petición HTTP reactiva
     * @return El valor de la cookie access_token, o null si no existe
     */
    private String getAccessTokenFromCookie(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(ACCESS_TOKEN_COOKIE);
        if (cookie == null) {
            return null;
        }
        return cookie.getValue();
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