package com.evecta.gateway.filter;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filtro que identifica las peticiones que el gateway reenvía a los servicios
 * internos.
 * <p>
 * El gateway sustituye cualquier {@code X-Internal-Key} entrante (un cliente
 * podría colarlo) por el valor de {@code INTERNAL_CHANNEL_KEY}. Auth, core y
 * plate exigen esa cabecera en cada petición que reciben, de modo que una
 * llamada que llegue saltándose al gateway queda descartada con un 403.
 * <p>
 * Si {@code INTERNAL_CHANNEL_KEY} no está definida la cabecera no se envía y el
 * filtro queda inactivo; es el interruptor de apagado si hay que desactivar la
 * guardia sin reimplementar nada.
 */
@Component
@Order(-90)
public class ChannelKeyFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(ChannelKeyFilter.class);

    static final String CHANNEL_KEY_HEADER = "X-Internal-Key";

    private final String channelKey;

    public ChannelKeyFilter(@Value("${internal.channel.key:}") String channelKey) {
        this.channelKey = channelKey == null ? "" : channelKey.trim();
    }

    @PostConstruct
    void logStatus() {
        if (channelKey.isEmpty()) {
            log.warn("[!] INTERNAL_CHANNEL_KEY no definida: no se envía la cabecera {} y los "
                    + "servicios internos no podrán validar el canal.", CHANNEL_KEY_HEADER);
        } else {
            log.info("[+] Canal interno activo: cada petición saliente llevará {}", CHANNEL_KEY_HEADER);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest mutated = exchange.getRequest().mutate().headers(headers -> {
            // Primero se limpia lo que haya traído el cliente: si no se configura
            // la clave, al menos nadie puede colarla a través del gateway.
            headers.remove(CHANNEL_KEY_HEADER);
            if (!channelKey.isEmpty()) {
                headers.set(CHANNEL_KEY_HEADER, channelKey);
            }
        }).build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }
}
