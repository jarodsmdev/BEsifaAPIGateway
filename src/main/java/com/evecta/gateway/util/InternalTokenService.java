package com.evecta.gateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Genera el <b>token interno</b> ({@code X-Auth-Identity}) que core-sifa debe
 * aceptar para confiar en la identidad y roles de una petición.
 * <p>
 * El token interno es un JWT de <b>corta duración</b> (60s) firmado con un secreto
 * dedicado ({@code INTERNAL_JWT_SECRET}), distinto del JWT de sesión de usuarios.
 * Esto permite que los servicios downstream (core) validen la identidad verificada
 * por el Gateway sin confiar en cabeceras planas falsificables
 * ({@code X-Auth-User} / {@code X-Auth-Roles}).
 */
@Component
public class InternalTokenService {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenService.class);

    private static final long EXPIRATION_SECONDS = 60;

    @Value("${jwt.internal.secret}")
    private String secret;

    @Value("${jwt.internal.issuer}")
    private String issuer;

    @Value("${jwt.internal.audience}")
    private String audience;

    /**
     * Deriva la clave HMAC para firmar el token interno.
     * Acepta el secreto en Base64 (formato usado en el resto del sistema) o plano.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Genera un token interno firmado con el secreto interno del sistema.
     *
     * @param username email del usuario autenticado por el Gateway
     * @param roles    roles verificados del usuario (ej: {@code [USER_ADMIN]})
     * @return JWT compacto con issuer/audience internos y expiración de 60s
     */
    public String generateToken(String username, List<String> roles) {
        if (roles == null) {
            roles = List.of();
        }
        log.debug("[+] Generando token interno para: {}", username);
        return Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_SECONDS * 1000))
                .claim("roles", roles)
                .signWith(getSigningKey())
                .compact();
    }
}