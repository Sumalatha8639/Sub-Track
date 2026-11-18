package com.subtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.subtrack.dto.auth.LoginRequest;
import com.subtrack.dto.auth.RegisterRequest;
import com.subtrack.dto.auth.ResetPasswordRequest;
import com.subtrack.entity.RefreshToken;
import com.subtrack.entity.User;
import com.subtrack.entity.UserToken;
import com.subtrack.enums.TokenType;
import com.subtrack.exception.BadRequestException;
import com.subtrack.exception.ConflictException;
import com.subtrack.exception.UnauthorizedException;
import com.subtrack.notification.AccountEmailEvent;
import com.subtrack.repository.RefreshTokenRepository;
import com.subtrack.repository.UserRepository;
import com.subtrack.repository.UserTokenRepository;
import com.subtrack.security.JwtService;
import com.subtrack.security.TokenHasher;
import com.subtrack.support.MutableClock;
import com.subtrack.support.TestProps;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final UserTokenRepository userTokens = mock(UserTokenRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // fast for tests
    private final TokenHasher hasher = new TokenHasher();
    private final MutableClock clock = new MutableClock(NOW);

    private AuthService service;

    @BeforeEach
    void setUp() {
        JwtService jwt = new JwtService(TestProps.create(), clock);
        service = new AuthService(users, refreshTokens, userTokens, encoder, jwt, hasher, events,
                TestProps.create(), clock);
    }

    // ---- register ------------------------------------------------------------

    @Test
    void registerRejectsExistingEmail() {
        when(users.existsByEmailIgnoreCase("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Asha", " Asha@Example.com ", "password1", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void registerCreatesUserSendsVerificationAndLogsIn() {
        var result = service.register(new RegisterRequest(" Asha ", "Asha@Example.com", "password1", null));

        User saved = result.user();
        assertThat(saved.getEmail()).isEqualTo("asha@example.com");
        assertThat(saved.getName()).isEqualTo("Asha");
        assertThat(saved.getTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(encoder.matches("password1", saved.getPasswordHash())).isTrue();

        ArgumentCaptor<AccountEmailEvent> event = ArgumentCaptor.forClass(AccountEmailEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().kind()).isEqualTo(AccountEmailEvent.Kind.VERIFY_EMAIL);
        assertThat(event.getValue().to()).isEqualTo("asha@example.com");

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void registerRejectsUnknownTimezone() {
        assertThatThrownBy(() -> service.register(new RegisterRequest("A", "a@example.com", "password1", "Mars/Base")))
                .isInstanceOf(BadRequestException.class);
    }

    // ---- login ---------------------------------------------------------------

    @Test
    void loginWithCorrectPasswordIssuesTokens() {
        User user = user(1L, "asha@example.com", "password1");
        when(users.findByEmailIgnoreCase("asha@example.com")).thenReturn(Optional.of(user));

        var result = service.login(new LoginRequest("ASHA@example.com", "password1"));

        assertThat(result.user()).isSameAs(user);
        assertThat(result.expiresIn()).isEqualTo(900);
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailGivesSame401() {
        User user = user(1L, "asha@example.com", "password1");
        when(users.findByEmailIgnoreCase("asha@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "wrong-pass")))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid email or password");
        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "password1")))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid email or password");
    }

    // ---- refresh -------------------------------------------------------------

    @Test
    void refreshRotatesToken() {
        String raw = "old-token";
        RefreshToken stored = refreshToken(user(1L, "a@example.com", "x"), raw, NOW.plus(Duration.ofDays(7)));
        when(refreshTokens.findByTokenHash(hasher.hash(raw))).thenReturn(Optional.of(stored));

        var result = service.refresh(raw);

        assertThat(stored.getRevokedAt()).isEqualTo(NOW);
        assertThat(result.refreshToken()).isNotEqualTo(raw);
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void reusingRevokedTokenRevokesAllSessions() {
        String raw = "stolen-token";
        RefreshToken stored = refreshToken(user(7L, "a@example.com", "x"), raw, NOW.plus(Duration.ofDays(7)));
        stored.setRevokedAt(NOW.minus(Duration.ofMinutes(5)));
        when(refreshTokens.findByTokenHash(hasher.hash(raw))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refresh(raw)).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokens).revokeAllForUser(7L, NOW);
    }

    @Test
    void reuseWithinGraceWindowIsTreatedAsConcurrentRefresh() {
        String raw = "just-rotated";
        RefreshToken stored = refreshToken(user(7L, "a@example.com", "x"), raw, NOW.plus(Duration.ofDays(7)));
        stored.setRevokedAt(NOW.minus(Duration.ofSeconds(3)));
        when(refreshTokens.findByTokenHash(hasher.hash(raw))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refresh(raw)).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokens, never()).revokeAllForUser(anyLong(), any());
    }

    @Test
    void expiredOrMissingRefreshTokenIsRejected() {
        String raw = "expired";
        RefreshToken stored = refreshToken(user(1L, "a@example.com", "x"), raw, NOW.minusSeconds(1));
        when(refreshTokens.findByTokenHash(hasher.hash(raw))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refresh(raw)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.refresh(null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.refresh("unknown")).isInstanceOf(UnauthorizedException.class);
    }

    // ---- verification & reset ------------------------------------------------

    @Test
    void verifyEmailMarksUserVerifiedAndTokenUsed() {
        User user = user(1L, "a@example.com", "x");
        UserToken token = userToken(user, TokenType.EMAIL_VERIFICATION, NOW.plus(Duration.ofHours(1)));
        when(userTokens.findByTokenHashAndType(hasher.hash("v"), TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        service.verifyEmail("v");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void expiredOrUsedVerificationLinkIsRejected() {
        User user = user(1L, "a@example.com", "x");
        UserToken expired = userToken(user, TokenType.EMAIL_VERIFICATION, NOW.minusSeconds(1));
        when(userTokens.findByTokenHashAndType(hasher.hash("old"), TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.verifyEmail("old")).isInstanceOf(BadRequestException.class);
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void forgotPasswordForUnknownEmailDoesNothingVisible() {
        when(users.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        service.forgotPassword("ghost@example.com");

        verify(events, never()).publishEvent(any());
    }

    @Test
    void forgotPasswordSendsResetEmailAndInvalidatesOlderLinks() {
        User user = user(3L, "a@example.com", "x");
        when(users.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(user));

        service.forgotPassword("a@example.com");

        verify(userTokens).invalidateOpen(3L, TokenType.PASSWORD_RESET, NOW);
        ArgumentCaptor<AccountEmailEvent> event = ArgumentCaptor.forClass(AccountEmailEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().kind()).isEqualTo(AccountEmailEvent.Kind.RESET_PASSWORD);
    }

    @Test
    void resetPasswordChangesHashVerifiesEmailAndLogsOutEverywhere() {
        User user = user(5L, "a@example.com", "old-password");
        UserToken token = userToken(user, TokenType.PASSWORD_RESET, NOW.plus(Duration.ofMinutes(10)));
        when(userTokens.findByTokenHashAndType(hasher.hash("r"), TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));

        service.resetPassword(new ResetPasswordRequest("r", "new-password"));

        assertThat(encoder.matches("new-password", user.getPasswordHash())).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        verify(refreshTokens).revokeAllForUser(eq(5L), eq(NOW));
    }

    // ---- helpers -------------------------------------------------------------

    private User user(Long id, String email, String password) {
        User u = new User();
        u.setId(id);
        u.setName("Test");
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        return u;
    }

    private RefreshToken refreshToken(User user, String raw, Instant expiresAt) {
        RefreshToken t = new RefreshToken();
        t.setUser(user);
        t.setTokenHash(hasher.hash(raw));
        t.setExpiresAt(expiresAt);
        return t;
    }

    private UserToken userToken(User user, TokenType type, Instant expiresAt) {
        UserToken t = new UserToken();
        t.setUser(user);
        t.setType(type);
        t.setExpiresAt(expiresAt);
        return t;
    }
}
