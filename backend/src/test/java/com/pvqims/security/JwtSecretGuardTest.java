package com.pvqims.security;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecretGuardTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(JwtSecretGuard.class);

    private ApplicationContextRunner withSecret(String secret) {
        return runner.withBean(JwtService.JwtProperties.class,
            () -> new JwtService.JwtProperties(secret, Duration.ofHours(8)));
    }

    @Test
    void defaultSecretIsRejectedOutsideLocalAndTestProfiles() {
        withSecret(JwtSecretGuard.DEFAULT_SECRET)
            .withPropertyValues("spring.profiles.active=prod")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET");
            });
    }

    @Test
    void defaultSecretIsAllowedInLocalAndTestProfiles() {
        for (String profile : new String[] {"local", "test"}) {
            withSecret(JwtSecretGuard.DEFAULT_SECRET)
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> assertThat(context).hasNotFailed());
        }
    }

    @Test
    void customSecretIsAllowedInAnyProfile() {
        withSecret("a-real-deployment-secret-at-least-32-bytes")
            .withPropertyValues("spring.profiles.active=prod")
            .run(context -> assertThat(context).hasNotFailed());
    }
}
