package com.subtrack.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String name,
        @Pattern(regexp = "^$|^[0-9+()\\- ]{7,20}$", message = "must be a valid phone number") String phone,
        @NotBlank @Size(max = 50) String timezone) {
}
