package com.evecta.gateway.filter;

import com.evecta.gateway.service.TokenValidationService;
import com.evecta.gateway.util.InternalTokenService;
import com.evecta.gateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Valida la sanitización de cabeceras {@code X-Auth-*}: un cliente que se
 * conecte directo al servicio y las falsifique no debe poder colarlas a través
 * del gateway. En las rutas protegidas las reconstruye desde el JWT validado.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TokenValidationService tokenValidationService;

    @Mock
    private InternalTokenService internalTokenService;

    private JwtAuthenticationFilter filter;

    private AtomicReference<ServerHttpRequest> captured;
    private AtomicBoolean forwarded;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtUtil, tokenValidationService, internalTokenService);
        captured = new AtomicReference<>();
        forwarded = new AtomicBoolean();
    }

    private GatewayFilterChain chain() {
        return ex -> {
            captured.set(ex.getRequest());
            forwarded.set(true);
            return Mono.empty();
        };
    }

    @Test
    void filter_rutaPublica_eliminaCabecerasXAuthFalsificadas() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/api/v1/auth/login")
                        .header("X-Auth-User", "hacker@test.cl")
                        .header("X-Auth-Roles", "USER_ADMIN")
                        .header("X-Auth-Token-Valid", "true")
                        .header("X-Auth-Identity", "token-falsificado")
                        .build());

        filter.filter(exchange, chain()).block();

        assertThat(forwarded).isTrue();
        ServerHttpRequest out = captured.get();
        assertThat(out.getHeaders().getFirst("X-Auth-User")).isNull();
        assertThat(out.getHeaders().getFirst("X-Auth-Roles")).isNull();
        assertThat(out.getHeaders().getFirst("X-Auth-Token-Valid")).isNull();
        assertThat(out.getHeaders().getFirst("X-Auth-Identity")).isNull();
        assertThat(out.getHeaders().getFirst("Authorization")).isNull();
    }

    @Test
    void filter_rutaDocumentacion_eliminaCabecerasXAuthFalsificadas() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v3/api-docs")
                        .header("X-Auth-User", "hacker@test.cl")
                        .header("X-Auth-Identity", "token-falsificado")
                        .build());

        filter.filter(exchange, chain()).block();

        ServerHttpRequest out = captured.get();
        assertThat(out.getHeaders().getFirst("X-Auth-User")).isNull();
        assertThat(out.getHeaders().getFirst("X-Auth-Identity")).isNull();
    }

    @Test
    void filter_rutaProtegida_regeneraCabecerasDesdeJwtValidado() {
        String token = "session-jwt-valido";
        given(jwtUtil.validateToken(token)).willReturn(true);
        given(tokenValidationService.validateToken(token)).willReturn(Mono.just("valid"));
        given(jwtUtil.extractUsername(token)).willReturn("admin@test.cl");
        given(jwtUtil.extractRoles(token)).willReturn(List.of("USER_APP"));
        given(internalTokenService.generateToken("admin@test.cl", List.of("USER_APP")))
                .willReturn("jwt-interno");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/plate/api/v1/detect")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Auth-User", "hacker@test.cl")
                        .header("X-Auth-Roles", "USER_ADMIN")
                        .header("X-Auth-Identity", "token-falsificado")
                        .build());

        filter.filter(exchange, chain()).block();

        ServerHttpRequest out = captured.get();
        assertThat(out.getHeaders().getFirst("X-Auth-User")).isEqualTo("admin@test.cl");
        assertThat(out.getHeaders().getFirst("X-Auth-Roles")).isEqualTo("USER_APP");
        assertThat(out.getHeaders().getFirst("X-Auth-Token-Valid")).isEqualTo("true");
        assertThat(out.getHeaders().getFirst("X-Auth-Identity")).isEqualTo("jwt-interno");
        assertThat(out.getHeaders().getFirst("Authorization")).isEqualTo("Bearer " + token);
    }

    @Test
    void filter_preflightOPTIONS_completaSinReenviar() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/plate/api/v1/detect")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .build());

        filter.filter(exchange, chain()).block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(200);
    }
}