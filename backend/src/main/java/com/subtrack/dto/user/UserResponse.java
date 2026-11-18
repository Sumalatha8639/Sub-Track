package com.subtrack.dto.user;

import com.subtrack.entity.User;
import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        String phone,
        String timezone,
        boolean emailVerified,
        Instant createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getTimezone(),
                u.isEmailVerified(), u.getCreatedAt());
    }
}
