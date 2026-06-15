package com.evecta.gateway.ratelimit;

public class RateLimitRule {
    private String pathPattern;
    private int maxRequests;
    private long windowSeconds;

    public String getPathPattern() { return pathPattern; }
    public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }
    public int getMaxRequests() { return maxRequests; }
    public void setMaxRequests(int maxRequests) { this.maxRequests = maxRequests; }
    public long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(long windowSeconds) { this.windowSeconds = windowSeconds; }
}
