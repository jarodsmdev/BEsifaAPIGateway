package com.evecta.gateway.filter;

import com.evecta.gateway.config.RateLimitProperties;
import com.evecta.gateway.ratelimit.RateLimitRule;
import com.evecta.gateway.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Component
@Order(-101)
public class RateLimitingFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final PathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitingFilter(RateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (request.getMethod() == HttpMethod.OPTIONS) return chain.filter(exchange);

        String path = request.getURI().getPath();
        String clientIp = extractClientIp(request);
        if (clientIp == null) return chain.filter(exchange);

        for (RateLimitRule rule : properties.getRules()) {
            if (pathMatcher.match(rule.getPathPattern(), path)) {
                String key = clientIp + ":" + rule.getPathPattern();
                if (!rateLimiter.isAllowed(key, rule.getMaxRequests(), rule.getWindowSeconds())) {
                    log.warn("[RATE LIMIT BLOCKED] IP {} | {}/{} en {}s | regla={}",
                            clientIp, rule.getMaxRequests(), rule.getMaxRequests(),
                            rule.getWindowSeconds(), rule.getPathPattern());
                    return rateLimitExceededResponse(exchange, rule.getWindowSeconds());
                }
                break;
            }
        }

        return chain.filter(exchange);
    }

    private String extractClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst(X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        InetSocketAddress remote = request.getRemoteAddress();
        return (remote != null && remote.getAddress() != null) ? remote.getAddress().getHostAddress() : null;
    }

    private Mono<Void> rateLimitExceededResponse(ServerWebExchange exchange, long windowSeconds) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set("Content-Type", "application/json");
        response.getHeaders().set("Retry-After", String.valueOf(windowSeconds));

        String body = String.format(
                "{\"error\":\"Demasiadas solicitudes\",\"message\":\"Has excedido el límite de solicitudes. Intenta de nuevo en %d segundos.\",\"retryAfter\":%d}",
                windowSeconds, windowSeconds);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }
}
