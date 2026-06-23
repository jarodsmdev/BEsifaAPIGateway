package com.evecta.gateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil — Validación y extracción de tokens JWT")
class JwtUtilTest {

    // Clave de prueba genérica (segura para Git, el test unitario es autocontenido)
    private static final String JWT_SECRET =
            "dGhpc0lzQUR1bW15U2VjcmV0S2V5Rm9yVW5pdFRlc3RpbmdDYW5CZUFueXRoaW5nMTIzNDU2Nzg=";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // Inyectamos el secret directamente sin levantar Spring Context
        ReflectionTestUtils.setField(jwtUtil, "secret", JWT_SECRET);
    }

    // -------------------------------------------------------------------------
    // Helpers para construir tokens de prueba
    // -------------------------------------------------------------------------

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(JWT_SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Genera un token válido con el secret correcto */
    private String buildValidToken(String username, List<String> roles, long expirationMs) {
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /** Genera un token ya expirado */
    private String buildExpiredToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000)) // expiró hace 5 seg
                .signWith(getSigningKey())
                .compact();
    }

    /** Genera un token firmado con una clave DIFERENTE (inválida para el gateway) */
    private String buildTokenWithWrongKey(String username) {
        // Usamos una clave diferente de 256 bits
        String wrongSecret = "V3JvbmdTZWNyZXRLZXlGb3JUZXN0aW5nUHVycG9zZXNPbmx5MjA=";
        byte[] keyBytes = Decoders.BASE64.decode(wrongSecret);
        SecretKey wrongKey = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(wrongKey)
                .compact();
    }

    // =========================================================================
    // validateToken()
    // =========================================================================

    @Test
    @DisplayName("validateToken → token válido retorna true")
    void validateToken_conTokenValido_retornaTrue() {
        String token = buildValidToken("admin", List.of("ADMIN"), 3_600_000);

        boolean result = jwtUtil.validateToken(token);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("validateToken → token con firma inválida retorna false")
    void validateToken_conFirmaInvalida_retornaFalse() {
        String token = buildTokenWithWrongKey("admin");

        boolean result = jwtUtil.validateToken(token);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("validateToken → token expirado retorna false")
    void validateToken_conTokenExpirado_retornaFalse() {
        String token = buildExpiredToken("admin");

        boolean result = jwtUtil.validateToken(token);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("validateToken → cadena vacía retorna false")
    void validateToken_conCadenaVacia_retornaFalse() {
        boolean result = jwtUtil.validateToken("");

        assertThat(result).isFalse();
    }

    // =========================================================================
    // extractUsername()
    // =========================================================================

    @Test
    @DisplayName("extractUsername → retorna el subject correcto del token")
    void extractUsername_retornaSubjectCorrecto() {
        String token = buildValidToken("jarod", List.of("ADMIN"), 3_600_000);

        String username = jwtUtil.extractUsername(token);

        assertThat(username).isEqualTo("jarod");
    }

    // =========================================================================
    // extractRoles()
    // =========================================================================

    @Test
    @DisplayName("extractRoles → retorna la lista de roles del token")
    void extractRoles_retornaListaDeRoles() {
        List<String> expectedRoles = List.of("ADMIN", "USER");
        String token = buildValidToken("admin", expectedRoles, 3_600_000);

        List<String> roles = jwtUtil.extractRoles(token);

        assertThat(roles)
                .isNotNull()
                .hasSize(2)
                .containsExactlyInAnyOrder("ADMIN", "USER");
    }

    // =========================================================================
    // isTokenExpired()
    // =========================================================================

    @Test
    @DisplayName("isTokenExpired → token vigente retorna false")
    void isTokenExpired_conTokenVigente_retornaFalse() {
        String token = buildValidToken("admin", List.of("ADMIN"), 3_600_000); // 1 hora

        boolean expired = jwtUtil.isTokenExpired(token);

        assertThat(expired).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired → token expirado retorna true")
    void isTokenExpired_conTokenExpirado_retornaTrue() {
        String token = buildExpiredToken("admin");

        boolean expired = jwtUtil.isTokenExpired(token);

        assertThat(expired).isTrue();
    }
}
