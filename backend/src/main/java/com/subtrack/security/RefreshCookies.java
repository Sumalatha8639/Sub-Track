package com.subtrack.security;

import com.subtrack.config.AppProperties;
import com.subtrack.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token cookie: httpOnly (JavaScript can't read it), scoped to /api/auth so it is
 * only sent to the auth endpoints, Secure + SameSite=None in production.
 */
@Component
@RequiredArgsConstructor
public class RefreshCookies {

    public static final String NAME = "subtrack_refresh";
    private static final String PATH = "/api/auth";

    private final AppProperties props;

    public ResponseCookie create(String rawToken) {
        return base(rawToken).maxAge(Duration.ofDays(props.jwt().refreshTokenDays())).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    /**
     * The cookie is sent automatically by the browser, so endpoints that use it reject
     * requests coming from any other site (CSRF protection).
     */
    public void requireTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !origin.equals(props.frontendUrl())) {
            throw new ForbiddenException("Origin not allowed");
        }
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(props.auth().refreshCookieSecure())
                .sameSite(props.auth().refreshCookieSameSite())
                .path(PATH);
    }
}
