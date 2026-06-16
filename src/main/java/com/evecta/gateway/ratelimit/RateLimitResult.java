package com.evecta.gateway.ratelimit;

public record RateLimitResult(boolean allowed, int currentCount, int limit, long windowSeconds) {
}
