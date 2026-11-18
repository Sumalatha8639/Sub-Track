package com.subtrack.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.subtrack.config.AppProperties;
import com.subtrack.entity.User;
import com.subtrack.support.MutableClock;
import com.subtrack.support.TestProps;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T10:00:00Z"));
    private final JwtService jwt = new JwtService(TestProps.create(), clock);

    @Test
    void createdTokenParsesBackToTheSameUser() {
        String token = jwt.createAccessToken(user(42L, "asha@example.com"));

        assertThat(jwt.parse(token)).contains(new AuthUser(42L, "asha@example.com"));
        assertThat(jwt.accessTokenTtlSeconds()).isEqualTo(15 * 60);
    }

    @Test
    void tokenIsRejectedAfterFifteenMinutes() {
        String token = jwt.createAccessToken(user(1L, "a@example.com"));

        clock.advance(Duration.ofMinutes(14));
        assertThat(jwt.parse(token)).isPresent();

        clock.advance(Duration.ofMinutes(2));
        assertThat(jwt.parse(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        AppProperties other = new AppProperties(TestProps.FRONTEND, "Asia/Kolkata",
                new AppProperties.Jwt("another-secret-another-secret-another-secret-0123", 15, 7),
                null, null, null, null);
        String foreign = new JwtService(other, clock).createAccessToken(user(1L, "a@example.com"));

        assertThat(jwt.parse(foreign)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwt.parse("not.a.jwt")).isEmpty();
        assertThat(jwt.parse("")).isEmpty();
    }

    @Test
    void shortSecretFailsFast() {
        AppProperties weak = new AppProperties(TestProps.FRONTEND, "Asia/Kolkata",
                new AppProperties.Jwt("too-short", 15, 7), null, null, null, null);

        assertThatThrownBy(() -> new JwtService(weak, clock)).isInstanceOf(IllegalStateException.class);
    }

    private static User user(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }
}
