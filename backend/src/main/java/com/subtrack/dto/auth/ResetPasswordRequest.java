package com.subtrack.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String newPassword) {
}
