package com.subtrack.controller;

import com.subtrack.dto.auth.AuthResponse;
import com.subtrack.dto.auth.EmailRequest;
import com.subtrack.dto.auth.LoginRequest;
import com.subtrack.dto.auth.RegisterRequest;
import com.subtrack.dto.auth.ResetPasswordRequest;
import com.subtrack.dto.auth.TokenRequest;
import com.subtrack.dto.common.MessageResponse;
import com.subtrack.dto.user.ChangePasswordRequest;
import com.subtrack.dto.user.UserResponse;
import com.subtrack.security.AuthUser;
import com.subtrack.security.RefreshCookies;
import com.subtrack.service.AuthService;
import com.subtrack.service.AuthService.AuthResult;
import com.subtrack.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RefreshCookies refreshCookies;

    /** Creates the account, sends a verification email and logs the user in. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return withSession(HttpStatus.CREATED, authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return withSession(HttpStatus.OK, authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        refreshCookies.requireTrustedOrigin(request);
        return withSession(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        refreshCookies.requireTrustedOrigin(request);
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody TokenRequest req) {
        authService.verifyEmail(req.token());
        return new MessageResponse("Email verified. You'll now receive renewal reminders.");
    }

    @PostMapping("/resend-verification")
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest req) {
        authService.resendVerification(req.email());
        return new MessageResponse("If that account exists and isn't verified yet, we've sent a new verification email.");
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest req) {
        authService.forgotPassword(req.email());
        return new MessageResponse("If an account exists for that email, we've sent a password reset link.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return new MessageResponse("Password updated. Please log in with your new password.");
    }

    /**
     * Needs a valid access token (see SecurityConfig). Lives under /api/auth so the browser sends
     * the refresh cookie: every other session is logged out, this one stays logged in.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody ChangePasswordRequest req,
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        refreshCookies.requireTrustedOrigin(request);
        userService.changePassword(user.id(), req, refreshToken);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> withSession(HttpStatus status, AuthResult result) {
        AuthResponse body = AuthResponse.bearer(result.accessToken(), result.expiresIn(),
                UserResponse.from(result.user()));
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookies.create(result.refreshToken()).toString())
                .body(body);
    }
}
