package com.evecta.gateway.config;

import com.evecta.gateway.ratelimit.RateLimitRule;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {
    private List<RateLimitRule> rules = new ArrayList<>();

    public List<RateLimitRule> getRules() { return rules; }
    public void setRules(List<RateLimitRule> rules) { this.rules = rules; }
}
