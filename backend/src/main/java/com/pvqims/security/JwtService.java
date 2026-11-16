package com.pvqims.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * 签发与校验 JWT。密钥与有效期来自 {@code pvqims.jwt} 配置。
 */
@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issue(Long userId, String username, String role) {
        Instant expiresAt = Instant.now().plus(properties.ttl());
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
            .subject(userId.toString())
            .id(jti)
            .claim("username", username)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact();
        return new IssuedToken(token, jti, expiresAt);
    }

    /**
     * 校验签名与有效期；任何非法令牌都以 {@link io.jsonwebtoken.JwtException} 抛出。
     */
    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * 令牌有效期，供 Redis 会话表设置同样的 TTL。
     */
    public Duration ttl() {
        return properties.ttl();
    }

    public record IssuedToken(String token, String jti, Instant expiresAt) {
    }

    @ConfigurationProperties("pvqims.jwt")
    public record JwtProperties(String secret, Duration ttl) {
    }
}
