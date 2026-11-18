package com.subtrack.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Creates random opaque tokens (refresh, email verification, password reset) and
 * hashes them. Only the SHA-256 hash is stored, so a database leak does not leak usable tokens.
 */
@Component
public class TokenHasher {

    private static final int TOKEN_BYTES = 32; // 256 bits
    private final SecureRandom random = new SecureRandom();

    /** URL-safe random token to give to the user (cookie value or email link). */
    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lower-case hex SHA-256, 64 characters. */
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
