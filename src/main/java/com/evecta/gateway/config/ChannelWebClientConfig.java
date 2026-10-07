package com.evecta.gateway.config;

import com.evecta.gateway.filter.ChannelKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Añade la cabecera del canal interno a las llamadas HTTP directas que el
 * gateway hace a otros servicios (por ejemplo, {@code TokenValidationService}
 * validando el token con auth-service).
 * <p>
 * El {@code ChannelKeyFilter} solo cubre las peticiones que el gateway reenvía
 * como ruta; las llamadas programáticas con {@code WebClient} la necesitan por
 * separado, si no los servicios internos las rechazan con un 403.
 */
@Configuration
public class ChannelWebClientConfig {

    @Bean
    public WebClientCustomizer channelKeyWebClientCustomizer(
            @Value("${internal.channel.key:}") String channelKey) {
        return builder -> {
            if (channelKey != null && !channelKey.isBlank()) {
                builder.defaultHeader(ChannelKeyFilter.CHANNEL_KEY_HEADER, channelKey.trim());
            }
        };
    }
}