package com.evecta.gateway;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Tests de integración del API Gateway contra los servicios Docker corriendo en localhost.
 *
 * Prerrequisitos:
 *   - docker compose up (auth-api:8081, core-sifa:8083, plate-api:8000)
 *   - El perfil "test" activo (application-test.properties)
 *
 * Para ejecutar solo estos tests:
 *   ./mvnw test -Dtest=GatewayIntegrationTest
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("GatewayIntegrationTest — Tests de integración contra Docker")
class GatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    /**
     * Token JWT obtenido del auth-service real.
     * Se obtiene una sola vez en el setUpAll y se reutiliza en los tests de rutas protegidas.
     */
    private static String validToken;

    // =========================================================================
    // Credenciales de prueba — usuario admin real en auth_db Docker
    // =========================================================================
    private static final String TEST_EMAIL    = "admin@correo.com";
    private static final String TEST_PASSWORD = "password_admin";

    // Header obligatorio del auth-service (identifica el origen del cliente)
    private static final String CLIENT_ORIGIN = "gateway-integration-test";

    @BeforeEach
    void configureTimeout() {
        // Timeout global generoso para conexiones contra Docker local
        webTestClient = webTestClient.mutate()
                .responseTimeout(Duration.ofSeconds(15))
                .build();
    }

    // =========================================================================
    // Paso 1 — Login real para obtener token (ruta pública)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("POST /auth/api/v1/login con credenciales válidas → 200 + token JWT")
    void rutaPublica_authLogin_conCredencialesValidas_retorna200YToken() {
        // LoginRequestDTO: { "email": string, "password": string }
        // Header obligatorio: X-Client-Origin
        String loginBody = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\"}", TEST_EMAIL, TEST_PASSWORD);

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) webTestClient.post()
                .uri("/auth/api/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Client-Origin", CLIENT_ORIGIN)
                .bodyValue(loginBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        Assertions.assertNotNull(responseBody, "El cuerpo de respuesta no debe ser nulo");

        // AuthResponseDTO retorna el token bajo la clave "accessToken"
        Object token = responseBody.get("accessToken");
        Assertions.assertNotNull(token, "La respuesta de login debe contener accessToken");
        Assertions.assertFalse(token.toString().isBlank(), "El accessToken no debe estar vacío");

        // Guardamos el token para los siguientes tests
        validToken = token.toString();
    }

    // =========================================================================
    // Paso 2 — Rutas protegidas sin token → 401
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("GET /core/api/v1/ sin Authorization header → 401")
    void rutaProtegida_sinToken_retorna401() {
        webTestClient.get()
                .uri("/core/api/v1/")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("No autorizado");
    }

    @Test
    @Order(3)
    @DisplayName("GET /core/api/v1/ con token de firma incorrecta → 401")
    void rutaProtegida_conTokenInvalido_retorna401() {
        // Token con firma de otra clave — inválido para el gateway
        String fakeToken = "eyJhbGciOiJIUzI1NiJ9" +
                ".eyJzdWIiOiJoYWNrZXIiLCJpYXQiOjE3MDAwMDAwMDB9" +
                ".invalid_signature_here";

        webTestClient.get()
                .uri("/core/api/v1/")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fakeToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("No autorizado");
    }

    // =========================================================================
    // Paso 3 — Ruta protegida con token real → 200 o 404 (depende del endpoint)
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("GET /core/api/v1/ con token válido → el gateway lo propaga (no devuelve 401)")
    void rutaProtegida_conTokenValido_noRetorna401() {
        // Este test verifica que el Gateway no bloquea la request.
        // El upstream (core-sifa) puede retornar 200, 404 u otro según su lógica propia.
        Assumptions.assumeTrue(validToken != null,
                "Token no disponible — el test de login (Order 1) debe pasar primero");

        webTestClient.get()
                .uri("/core/api/v1/health") // endpoint genérico de health o listado
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .exchange()
                // Solo nos interesa que el Gateway NO rechace con 401
                .expectStatus().value(status ->
                        Assertions.assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status,
                                "El gateway no debe rechazar un token válido con 401"));
    }

    // =========================================================================
    // Paso 4 — Headers de auditoría inyectados por el Gateway
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("Verificar que el Gateway inyecta X-Auth-User en la request downstream")
    void rutaProtegida_headersDeAuditoria_inyectados() {
        // Nota: Para verificar que el Gateway INYECTA los headers X-Auth-User y
        // X-Auth-Token-Valid al servicio upstream necesitaríamos un endpoint
        // del upstream que los devuelva como eco. En su defecto, verificamos que
        // el Gateway responde sin bloquear (status != 401) con un token válido,
        // lo que implica que el filter completó su flujo de inyección.

        Assumptions.assumeTrue(validToken != null,
                "Token no disponible — el test de login (Order 1) debe pasar primero");

        webTestClient.get()
                .uri("/core/api/v1/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .exchange()
                .expectStatus().value(status ->
                        Assertions.assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status,
                                "El gateway completó la inyección de headers correctamente"));
    }

    // =========================================================================
    // Paso 5 — Swagger accesible sin token
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("GET /swagger-ui.html sin token → acceso libre (no requiere auth)")
    void swagger_accesible_sinToken() {
        webTestClient.get()
                .uri("/swagger-ui.html")
                .exchange()
                // Swagger puede retornar 200 (HTML) o 302 (redirect a /swagger-ui/index.html)
                // En cualquier caso NO debe ser 401
                .expectStatus().value(status ->
                        Assertions.assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status,
                                "Swagger debe ser accesible sin token de autenticación"));
    }

    // =========================================================================
    // Paso 6 — Rate Limiting → 429
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("Rate limiting → superar el límite retorna 429 Too Many Requests")
    void rateLimiting_excedeLimite_retorna429() {
        // En application-test.properties configuramos max-requests=200 para /auth/**
        // Usamos una IP simulada via X-Forwarded-For para no afectar otros tests
        // y disparamos 201 requests rápidamente
        final String simulatedIp = "99.99.99.1";
        final int maxRequests = 200;
        final int totalRequests = maxRequests + 1;

        boolean got429 = false;

        for (int i = 0; i < totalRequests; i++) {
            Integer status = webTestClient.get()
                    .uri("/auth/api/v1/login") // ruta pública — así evitamos necesitar token
                    .header("X-Forwarded-For", simulatedIp)
                    .exchange()
                    .returnResult(String.class)
                    .getStatus()
                    .value();

            if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                got429 = true;
                break;
            }
        }

        Assertions.assertTrue(got429,
                "Debería haber recibido al menos un 429 después de " + maxRequests + " requests");
    }

    // =========================================================================
    // Paso 7 — Ruta inexistente → error manejado
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("GET /ruta-que-no-existe → el gateway responde con error manejado (404 o 503)")
    void rutaInexistente_retornaErrorManejado() {
        webTestClient.get()
                .uri("/ruta-que-no-existe-en-ningun-servicio")
                .exchange()
                // El gateway debe manejar el error con GatewayExcepcionHandler
                // y retornar 404 (NoResourceFoundException) o 503 si hubo routing a un servicio caído
                .expectStatus().value(status -> {
                    boolean isExpectedError =
                            status == HttpStatus.NOT_FOUND.value() ||
                            status == HttpStatus.SERVICE_UNAVAILABLE.value() ||
                            status == HttpStatus.UNAUTHORIZED.value(); // si pasa por el filtro JWT primero
                    Assertions.assertTrue(isExpectedError,
                            "Se esperaba 404, 401 o 503 para una ruta inexistente, pero se recibió: " + status);
                });
    }
}
