package com.subtrack.security;

/** The logged-in user, taken from the access token. Use with {@code @AuthenticationPrincipal}. */
public record AuthUser(Long id, String email) {
}
