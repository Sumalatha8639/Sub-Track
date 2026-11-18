package com.subtrack.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt only uses the first 72 bytes
        @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password,
        /** Optional IANA zone id; defaults to Asia/Kolkata. */
        @Size(max = 50) String timezone) {
}
