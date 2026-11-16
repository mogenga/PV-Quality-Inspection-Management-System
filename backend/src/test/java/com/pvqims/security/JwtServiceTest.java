package com.pvqims.security;

import java.time.Duration;
import java.time.Instant;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwt = new JwtService(
        new JwtService.JwtProperties("unit-test-secret-key-at-least-32-bytes", Duration.ofHours(8)));

    @Test
    void issuedTokenCarriesUserClaimsAndJti() {
        var issued = jwt.issue(7L, "engineer", "ENGINEER");
        Claims claims = jwt.parse(issued.token());
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("username", String.class)).isEqualTo("engineer");
        assertThat(claims.get("role", String.class)).isEqualTo("ENGINEER");
        assertThat(claims.getId()).isEqualTo(issued.jti());
        assertThat(issued.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void tamperedTokenIsRejected() {
        var issued = jwt.issue(7L, "engineer", "ENGINEER");
        assertThatThrownBy(() -> jwt.parse(issued.token() + "x"))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void nullAndEmptyTokensAreRejectedAsJwtException() {
        assertThatThrownBy(() -> jwt.parse(null)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.parse("")).isInstanceOf(JwtException.class);
    }
}
