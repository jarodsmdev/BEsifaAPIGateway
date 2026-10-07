package com.evecta.gateway.service;

import com.evecta.gateway.config.ChannelWebClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresión: las llamadas directas del gateway a los servicios internos
 * (validación del token con auth-service) deben llevar la cabecera del canal
 * interno, o auth las rechaza con un 403 y el usuario nunca entra.
 */
class TokenValidationServiceTest {

    private static final String KEY = "clave-compartida-de-canal-0123456789";

    @Test
    void validateToken_conClaveConfigurada_llevaCabeceraDeCanal() {
        AtomicReference<String> recibida = new AtomicReference<>();

        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((req, res) -> {
                    recibida.set(req.requestHeaders().get("X-Internal-Key"));
                    return res.status(200)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .sendString(Mono.just("{\"valid\":true}"));
                })
                .bindNow();

        try {
            WebClient.Builder builder = WebClient.builder();
            new ChannelWebClientConfig().channelKeyWebClientCustomizer(KEY).customize(builder);

            TokenValidationService service =
                    new TokenValidationService(builder, "http://localhost:" + server.port());

            String result = service.validateToken("un-token-jwt").block();

            assertThat(recibida.get()).isEqualTo(KEY);
            assertThat(result).isEqualTo("valid");
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void validateToken_sinClaveConfigurada_noLlevaCabecera() {
        AtomicReference<String> recibida = new AtomicReference<>();

        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((req, res) -> {
                    recibida.set(req.requestHeaders().get("X-Internal-Key"));
                    return res.status(200)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .sendString(Mono.just("{\"valid\":true}"));
                })
                .bindNow();

        try {
            WebClient.Builder builder = WebClient.builder();
            new ChannelWebClientConfig().channelKeyWebClientCustomizer("").customize(builder);

            TokenValidationService service =
                    new TokenValidationService(builder, "http://localhost:" + server.port());

            service.validateToken("un-token-jwt").block();

            assertThat(recibida.get()).isNull();
        } finally {
            server.disposeNow();
        }
    }
}