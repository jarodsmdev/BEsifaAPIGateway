package com.evecta.gateway.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class InMemorySlidingWindowRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(InMemorySlidingWindowRateLimiter.class);
    private final ConcurrentHashMap<String, Deque<Long>> storage = new ConcurrentHashMap<>();

    @Override
    public boolean isAllowed(String key, int maxRequests, long windowSeconds) {
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000);

        AtomicBoolean allowed = new AtomicBoolean(false);

        storage.compute(key, (k, deque) -> {
            if (deque == null) deque = new LinkedList<>();
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) deque.pollFirst();
            if (deque.size() < maxRequests) {
                deque.addLast(now);
                allowed.set(true);
            }
            return deque;
        });

        if (log.isDebugEnabled() && allowed.get()) {
            int count = storage.get(key).size();
            log.debug("[RATE LIMIT] {} | {}/{} en {}s", key, count, maxRequests, windowSeconds);
        }

        return allowed.get();
    }

    @Scheduled(fixedRate = 300000)
    public void cleanup() {
        int before = storage.size();
        if (before == 0) return;
        long cutoff = System.currentTimeMillis() - 3600000;
        storage.forEach((key, deque) -> {
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) deque.pollFirst();
            if (deque.isEmpty()) storage.remove(key);
        });
        int removed = before - storage.size();
        log.info("[RATE LIMIT CLEANUP] {} activas, eliminadas {} expiradas", storage.size(), removed);
    }
}
