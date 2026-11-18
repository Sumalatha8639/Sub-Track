package com.subtrack.service;

import com.subtrack.config.AppProperties;
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
import com.subtrack.util.Timezones;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, refresh-token rotation, email verification and password reset
 * (project plan §3.1, §7.1, §8.1).
 */
@Slf4j
@Service
public class AuthService {

    /**
     * A revoked refresh token presented again within this window is treated as two tabs
     * refreshing at the same time, not as theft: the request fails but the session survives.
     */
    static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final UserTokenRepository userTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHasher tokenHasher;
    private final ApplicationEventPublisher events;
    private final AppProperties props;
    private final Clock clock;
    /** Compared against when the email is unknown, so login takes the same time either way. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, UserTokenRepository userTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService, TokenHasher tokenHasher,
                       ApplicationEventPublisher events, AppProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.userTokens = userTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenHasher = tokenHasher;
        this.events = events;
        this.props = props;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /** Result of a successful login/refresh; the controller turns refreshToken into a cookie. */
    public record AuthResult(String accessToken, long expiresIn, User user, String refreshToken) {
    }

    // ---- registration & login ------------------------------------------------

    @Transactional
    public AuthResult register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setTimezone(Timezones.resolve(req.timezone(), props.defaultTimezone()));
        users.save(user);

        sendVerificationEmail(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(normalizeEmail(req.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(req.password(), hash);
        if (user == null || !matches) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return issueTokens(user);
    }

    // ---- refresh tokens ------------------------------------------------------

    /**
     * Rotates the refresh token. Presenting an already-revoked token (outside the grace window)
     * means it was probably stolen, so every session of that user is revoked.
     * noRollbackFor keeps that revocation even though we then throw 401.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Not logged in");
        }
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(tokenHasher.hash(rawRefreshToken))
                .orElseThrow(() -> new UnauthorizedException("Session expired, please log in again"));

        if (token.getRevokedAt() != null) {
            if (token.getRevokedAt().isBefore(now.minus(REUSE_GRACE))) {
                log.warn("Refresh token reuse detected for user {}; revoking all sessions", token.getUser().getId());
                refreshTokens.revokeAllForUser(token.getUser().getId(), now);
            }
            throw new UnauthorizedException("Session expired, please log in again");
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new UnauthorizedException("Session expired, please log in again");
        }

        token.setRevokedAt(now);
        return issueTokens(token.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(tokenHasher.hash(rawRefreshToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(clock.instant()));
    }

    // ---- email verification --------------------------------------------------

    @Transactional
    public void verifyEmail(String rawToken) {
        Instant now = clock.instant();
        UserToken token = userTokens.findByTokenHashAndType(tokenHasher.hash(rawToken), TokenType.EMAIL_VERIFICATION)
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BadRequestException("This verification link is invalid or has expired"));
        token.setUsedAt(now);
        token.getUser().setEmailVerified(true);
    }

    /** Always succeeds from the caller's view, so it can't be used to find out which emails are registered. */
    @Transactional
    public void resendVerification(String email) {
        users.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(u -> !u.isEmailVerified())
                .ifPresent(this::sendVerificationEmail);
    }

    // ---- password reset ------------------------------------------------------

    /** Always succeeds from the caller's view (no account enumeration). */
    @Transactional
    public void forgotPassword(String email) {
        users.findByEmailIgnoreCase(normalizeEmail(email)).ifPresent(user -> {
            String raw = createUserToken(user, TokenType.PASSWORD_RESET,
                    Duration.ofMinutes(props.auth().resetTokenMinutes()));
            events.publishEvent(new AccountEmailEvent(AccountEmailEvent.Kind.RESET_PASSWORD,
                    user.getEmail(), user.getName(), raw));
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        Instant now = clock.instant();
        UserToken token = userTokens.findByTokenHashAndType(tokenHasher.hash(req.token()), TokenType.PASSWORD_RESET)
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BadRequestException("This reset link is invalid or has expired"));
        token.setUsedAt(now);

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        // Opening the emailed link proves the user owns the address
        user.setEmailVerified(true);
        refreshTokens.revokeAllForUser(user.getId(), now);
    }

    // ---- helpers -------------------------------------------------------------

    private AuthResult issueTokens(User user) {
        String raw = tokenHasher.newToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(tokenHasher.hash(raw));
        token.setExpiresAt(clock.instant().plus(Duration.ofDays(props.jwt().refreshTokenDays())));
        refreshTokens.save(token);
        return new AuthResult(jwtService.createAccessToken(user), jwtService.accessTokenTtlSeconds(), user, raw);
    }

    private void sendVerificationEmail(User user) {
        String raw = createUserToken(user, TokenType.EMAIL_VERIFICATION,
                Duration.ofHours(props.auth().verificationTokenHours()));
        events.publishEvent(new AccountEmailEvent(AccountEmailEvent.Kind.VERIFY_EMAIL,
                user.getEmail(), user.getName(), raw));
    }

    /** Creates a single-use token and invalidates older open tokens of the same type. */
    private String createUserToken(User user, TokenType type, Duration ttl) {
        Instant now = clock.instant();
        userTokens.invalidateOpen(user.getId(), type, now);
        String raw = tokenHasher.newToken();
        UserToken token = new UserToken();
        token.setUser(user);
        token.setType(type);
        token.setTokenHash(tokenHasher.hash(raw));
        token.setExpiresAt(now.plus(ttl));
        userTokens.save(token);
        return raw;
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
