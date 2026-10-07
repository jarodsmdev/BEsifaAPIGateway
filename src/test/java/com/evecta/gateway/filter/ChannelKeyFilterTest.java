package com.evecta.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida que el gateway escribe la cabecera del canal interno en cada petición
 * saliente y que nunca deja pasar la que hubiera colado el cliente.
 */
class ChannelKeyFilterTest {

    private static final String KEY = "clave-compartida-de-canal-0123456789";

    /** Devuelve la petición tal como la recibe el resto de la cadena. */
    private ServerHttpRequest run(ChannelKeyFilter filter, MockServerHttpRequest inbound) {
        MockServerWebExchange exchange = MockServerWebExchange.from(inbound);
        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex.getRequest());
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        return captured.get();
    }

    @Test
    void filter_conClaveConfigurada_adjuntaCabeceraCorrecta() {
        ServerHttpRequest out = run(
                new ChannelKeyFilter(KEY),
                MockServerHttpRequest.get("/plate/api/v1/detect").build());

        assertThat(out.getHeaders().getFirst("X-Internal-Key")).isEqualTo(KEY);
    }

    @Test
    void filter_sinClaveConfigurada_noAdjuntaCabecera() {
        ServerHttpRequest out = run(
                new ChannelKeyFilter(""),
                MockServerHttpRequest.get("/plate/api/v1/detect").build());

        assertThat(out.getHeaders().getFirst("X-Internal-Key")).isNull();
    }

    /** Un cliente no puede colar su propia clave a través del gateway. */
    @Test
    void filter_sinClaveConfigurada_eliminaLaCabeceraDelCliente() {
        ServerHttpRequest out = run(
                new ChannelKeyFilter(""),
                MockServerHttpRequest.get("/plate/api/v1/detect")
                        .header("X-Internal-Key", "clave-falsificada-por-el-cliente")
                        .build());

        assertThat(out.getHeaders().getFirst("X-Internal-Key")).isNull();
    }

    /** Si el cliente coló una clave, el gateway la sustituye por la suya. */
    @Test
    void filter_sobrescribeLaCabeceraDelCliente() {
        ServerHttpRequest out = run(
                new ChannelKeyFilter(KEY),
                MockServerHttpRequest.get("/plate/api/v1/detect")
                        .header("X-Internal-Key", "clave-falsificada-por-el-cliente")
                        .build());

        assertThat(out.getHeaders().getFirst("X-Internal-Key")).isEqualTo(KEY);
    }
}
