package com.evecta.gateway.ratelimit;

public interface RateLimiter {
    RateLimitResult isAllowed(String key, int maxRequests, long windowSeconds);
}
