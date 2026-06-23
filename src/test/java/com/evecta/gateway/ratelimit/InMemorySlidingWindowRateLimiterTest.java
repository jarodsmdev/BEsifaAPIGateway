package com.evecta.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemorySlidingWindowRateLimiter — Algoritmo de ventana deslizante")
class InMemorySlidingWindowRateLimiterTest {

    private InMemorySlidingWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new InMemorySlidingWindowRateLimiter();
    }

    // =========================================================================
    // isAllowed()
    // =========================================================================

    @Test
    @DisplayName("isAllowed → primera request siempre es permitida")
    void isAllowed_primeraRequest_retornaTrue() {
        RateLimitResult result = rateLimiter.isAllowed("192.168.1.1:/auth/api/v1/**", 10, 60);

        assertThat(result.allowed()).isTrue();
    }

    @Test
    @DisplayName("isAllowed → requests dentro del límite, todas permitidas")
    void isAllowed_dentroDelLimite_todasPasan() {
        String key = "192.168.1.1:/api/v1/**";
        int maxRequests = 5;

        for (int i = 0; i < maxRequests; i++) {
            RateLimitResult result = rateLimiter.isAllowed(key, maxRequests, 60);
            assertThat(result.allowed())
                    .as("Request %d de %d debe ser permitida", i + 1, maxRequests)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("isAllowed → request que excede el límite retorna false")
    void isAllowed_excedeElLimite_retornaFalse() {
        String key = "10.0.0.1:/plate/api/v1/**";
        int maxRequests = 3;

        // Llenar el cupo
        for (int i = 0; i < maxRequests; i++) {
            rateLimiter.isAllowed(key, maxRequests, 60);
        }

        // La siguiente debe ser bloqueada
        RateLimitResult blocked = rateLimiter.isAllowed(key, maxRequests, 60);

        assertThat(blocked.allowed()).isFalse();
    }

    @Test
    @DisplayName("isAllowed → result contiene el límite y la ventana configurados")
    void isAllowed_resultContieneLimiteYVentana() {
        String key = "10.0.0.5:/test";
        int maxRequests = 5;
        long windowSeconds = 30;

        RateLimitResult result = rateLimiter.isAllowed(key, maxRequests, windowSeconds);

        assertThat(result.limit()).isEqualTo(maxRequests);
        assertThat(result.windowSeconds()).isEqualTo(windowSeconds);
        assertThat(result.currentCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("isAllowed → claves distintas tienen cupos independientes")
    void isAllowed_clavesDiferentes_tienenCuposIndependientes() {
        String key1 = "10.0.0.1:/auth/**";
        String key2 = "10.0.0.2:/auth/**";
        int maxRequests = 2;

        // Agotar cupo de key1
        rateLimiter.isAllowed(key1, maxRequests, 60);
        rateLimiter.isAllowed(key1, maxRequests, 60);
        RateLimitResult key1Blocked = rateLimiter.isAllowed(key1, maxRequests, 60);

        // key2 aún tiene cupo
        RateLimitResult key2Allowed = rateLimiter.isAllowed(key2, maxRequests, 60);

        assertThat(key1Blocked.allowed()).isFalse();
        assertThat(key2Allowed.allowed()).isTrue();
    }

    @Test
    @DisplayName("isAllowed → concurrencia no supera el límite máximo")
    void isAllowed_concurrencia_noSuperaElLimite() throws InterruptedException {
        String key = "concurrent-ip:/api/**";
        int maxRequests = 10;
        int totalThreads = 30;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        List<Runnable> tasks = new ArrayList<>();

        for (int i = 0; i < totalThreads; i++) {
            tasks.add(() -> {
                try {
                    latch.await();
                    if (rateLimiter.isAllowed(key, maxRequests, 60).allowed()) {
                        allowed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        tasks.forEach(executor::submit);
        latch.countDown(); // lanzar todos a la vez
        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(allowed.get())
                .as("No deben pasar más de %d requests aunque haya concurrencia", maxRequests)
                .isLessThanOrEqualTo(maxRequests);
    }

    // =========================================================================
    // cleanup()
    // =========================================================================

    @Test
    @DisplayName("cleanup → elimina entradas vacías del storage")
    @SuppressWarnings("unchecked")
    void cleanup_eliminaEntradasExpiradas() {
        // Llenar el rate limiter con algunas entradas
        rateLimiter.isAllowed("ip1:/test", 10, 60);
        rateLimiter.isAllowed("ip2:/test", 10, 60);

        // Accedemos al storage interno via reflexión y vaciamos las deques
        ConcurrentHashMap<String, Deque<Long>> storage =
                (ConcurrentHashMap<String, Deque<Long>>) ReflectionTestUtils
                        .getField(rateLimiter, "storage");

        assertThat(storage).isNotNull();

        // Vaciamos las deques para simular entradas expiradas
        storage.forEach((key, deque) -> deque.clear());

        // El cleanup debe eliminar las entradas con deques vacías
        rateLimiter.cleanup();

        assertThat(storage).isEmpty();
    }
}
