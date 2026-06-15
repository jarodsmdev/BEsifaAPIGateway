# Rate Limiting Implementation Plan

## Branch
`feat/rate-limiting`

## Problem
The API Gateway currently has **no rate limiting**. Public endpoints (`/auth/api/v1/**`, Swagger docs) are vulnerable to brute force and DDOS attacks. Protected endpoints have no safeguard against excessive usage.

## Solution

### Filter Chain Order

| Order | Filter | Descripción |
|-------|--------|-------------|
| `-101` | **RateLimitingFilter** | New — intercepts before JWT |
| `-100` | JwtAuthenticationFilter | Existing — validates JWT |
| `-2` | GatewayExcepcionHandler | Existing — global errors |
| `-1` | LogFilter | Existing — request/response logging |

The rate limiter runs **before JWT** to avoid wasting resources on tokens for requests that will be rejected by rate.

---

### Architecture (SOLID)

| Principle | Application |
|-----------|-------------|
| **SRP** | Each class has one responsibility |
| **OCP** | New backends (Redis, Bucket4j) via new `RateLimiter` impl — no filter changes |
| **LSP** | Any `RateLimiter` impl is substitutable |
| **ISP** | Minimal interface: single `isAllowed()` method |
| **DIP** | `RateLimitingFilter` depends on abstraction, not implementation |

### Patterns

| Pattern | Where |
|---------|-------|
| **Strategy** | `RateLimiter` interface with pluggable implementations |
| **Chain of Responsibility** | Spring Cloud Gateway `GlobalFilter` chain |
| **Dependency Injection** | Spring DI + `@ConditionalOnProperty` for backend swap |

---

### Algorithm: Sliding Window (per IP + rule)

```
Request → extract IP → match rule by path → key = ip + ":" + rule.pattern
    → isAllowed(key, maxRequests, windowSeconds)?
        → Yes: continue chain
        → No: 429 Too Many Requests + Retry-After header
```

Thread-safe via `ConcurrentHashMap.compute()` (atomic check-and-add).

Cleanup: `@Scheduled(fixedRate = 300000)` removes entries inactive > 1h.

---

### Files to Create (6)

| # | File | Package | Lines |
|---|------|---------|-------|
| 1 | `RateLimiter.java` | `ratelimit` | ~5 |
| 2 | `RateLimitRule.java` | `ratelimit` | ~22 |
| 3 | `InMemorySlidingWindowRateLimiter.java` | `ratelimit` | ~55 |
| 4 | `RateLimitProperties.java` | `config` | ~20 |
| 5 | `RateLimitConfig.java` | `config` | ~30 |
| 6 | `RateLimitingFilter.java` | `filter` | ~80 |

### Files to Modify (1)

| # | File | Change |
|---|------|--------|
| 7 | `application.properties` | +25 lines (rules + DDOS) |

---

### Rate Limit Rules

```
1. /auth/api/v1/**          →   5 req / 60s   (brute force protection)
2. /api/v1/dashboard/**     →  30 req / 60s   (dashboard)
3. /plate/api/v1/**         →  60 req / 60s   (plate recognition)
4. /core/api/v1/**          →  60 req / 60s   (core business)
5. /swagger-ui/**           →  20 req / 60s   (docs)
6. /**/v3/api-docs          →  20 req / 60s   (OpenAPI spec)
7. /**                      → 200 req / 60s   (global catch-all)
```

Evaluated in order — first match wins. `/**` is the safety net.

---

### Logging Strategy (SLF4J)

| Level | Action | Prefix |
|-------|--------|--------|
| `INFO` | Startup — rules loaded | `[RATE LIMIT]` |
| `INFO` | Cleanup — stale entries | `[RATE LIMIT CLEANUP]` |
| `WARN` | Request blocked (429) | `[RATE LIMIT BLOCKED]` |
| `DEBUG` | Request allowed (count) | `[RATE LIMIT]` |

---

### DDOS Protection (additional)

Added to `application.properties`:

```
spring.cloud.gateway.request-size.max-size=1MB
spring.cloud.gateway.httpclient.connect-timeout=5000
spring.cloud.gateway.httpclient.response-timeout=10s
```

---

### Future Scalability

Create `RedisSlidingWindowRateLimiter` implementing `RateLimiter`, then:

```properties
rate-limit.mode=redis   # activates @ConditionalOnProperty bean
```

No changes to filter, rules, or properties structure needed.
