package com.subtrack.service;

import com.subtrack.config.AppProperties;
import com.subtrack.dto.user.ChangePasswordRequest;
import com.subtrack.dto.user.UpdateProfileRequest;
import com.subtrack.dto.user.UserResponse;
import com.subtrack.entity.User;
import com.subtrack.exception.BadRequestException;
import com.subtrack.exception.NotFoundException;
import com.subtrack.repository.RefreshTokenRepository;
import com.subtrack.repository.UserRepository;
import com.subtrack.security.TokenHasher;
import com.subtrack.util.Timezones;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;
    private final AppProperties props;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return UserResponse.from(load(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = load(userId);
        user.setName(req.name().trim());
        user.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        user.setTimezone(Timezones.resolve(req.timezone(), props.defaultTimezone()));
        users.flush(); // so updatedAt in the response is current
        return UserResponse.from(user);
    }

    /** Changes the password and logs out every other session; the current one stays logged in. */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req, String currentRefreshToken) {
        User user = load(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));

        Instant now = clock.instant();
        if (currentRefreshToken == null || currentRefreshToken.isBlank()) {
            refreshTokens.revokeAllForUser(userId, now);
        } else {
            refreshTokens.revokeAllForUserExcept(userId, tokenHasher.hash(currentRefreshToken), now);
        }
    }

    private User load(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
