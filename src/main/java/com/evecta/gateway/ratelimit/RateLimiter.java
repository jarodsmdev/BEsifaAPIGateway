package com.evecta.gateway.ratelimit;

public interface RateLimiter {
    boolean isAllowed(String key, int maxRequests, long windowSeconds);
}
