package com.evecta.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.*;

@SpringBootApplication
public class GatewayApplication {

    private static final Logger log = LoggerFactory.getLogger(GatewayApplication.class);

    public static void main(String[] args) {
        log.info("[+] Iniciando Gateway Application...");
        SpringApplication.run(GatewayApplication.class, args);
        log.info("[+] Gateway Application iniciada correctamente");
    }

    @Bean
    @Order(-1)
    public GlobalFilter logFilter() {
        return (exchange, chain) -> {

            long startTime = System.currentTimeMillis();

            String method = exchange.getRequest().getMethod().name();
            String originalUri = exchange.getRequest().getURI().toString();

            // Obtener IP del cliente
            String clientIp = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Forwarded-For");

            if (clientIp == null) {
                var remoteAddress = exchange.getRequest().getRemoteAddress();
                clientIp = (remoteAddress != null && remoteAddress.getAddress() != null)
                        ? remoteAddress.getAddress().getHostAddress()
                        : "UNKNOWN";
            }

            log.info("═══════════════════════════════════════");
            log.info("[➡️ REQUEST] {} {}", method, originalUri);
            log.info("  → IP Cliente: {}", clientIp.split(",")[0]);

            return chain.filter(exchange).then(Mono.fromRunnable(() -> {

                long duration = System.currentTimeMillis() - startTime;

                URI routedUriAttr = exchange.getAttribute(java.util.Objects.requireNonNull(GATEWAY_REQUEST_URL_ATTR));
                String routedUri = routedUriAttr != null ? routedUriAttr.toString() : "UNKNOWN";

                Object routeAttr = exchange.getAttribute(java.util.Objects.requireNonNull(GATEWAY_ROUTE_ATTR));
                String routeId = routeAttr != null ? routeAttr.toString() : "UNKNOWN";

                var status = exchange.getResponse().getStatusCode();

                log.info("[⬅️ RESPONSE]");
                log.info("  → Route ID: {}", routeId);
                log.info("  → Destino: {} {}", method, routedUri);
                log.info("  → Status: {} | Tiempo: {} ms", status, duration);
                log.info("═══════════════════════════════════════");
            }));
        };
    }
}