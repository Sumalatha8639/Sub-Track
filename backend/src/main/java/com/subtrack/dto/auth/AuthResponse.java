package com.subtrack.dto.auth;

import com.subtrack.dto.user.UserResponse;

/** Returned by register, login and refresh. The refresh token travels separately as an httpOnly cookie. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    public static AuthResponse bearer(String accessToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
