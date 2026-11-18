package com.subtrack.controller;

import com.subtrack.dto.user.UpdateProfileRequest;
import com.subtrack.dto.user.UserResponse;
import com.subtrack.security.AuthUser;
import com.subtrack.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Profile of the logged-in user. Password change lives at /api/auth/change-password. */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal AuthUser user) {
        return userService.getProfile(user.id());
    }

    @PutMapping
    public UserResponse update(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(user.id(), req);
    }
}
