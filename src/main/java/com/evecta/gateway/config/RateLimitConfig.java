package com.evecta.gateway.config;

import com.evecta.gateway.ratelimit.RateLimitRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@EnableScheduling
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);
    private final RateLimitProperties properties;

    public RateLimitConfig(RateLimitProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void logRules() {
        log.info("[RATE LIMIT] {} reglas cargadas:", properties.getRules().size());
        for (int i = 0; i < properties.getRules().size(); i++) {
            RateLimitRule r = properties.getRules().get(i);
            log.info("  [{}] {} → {} req / {}s", i + 1, r.getPathPattern(), r.getMaxRequests(), r.getWindowSeconds());
        }
    }
}
