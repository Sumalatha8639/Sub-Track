package com.subtrack.controller;

import com.subtrack.dto.common.PageResponse;
import com.subtrack.dto.notification.NotificationResponse;
import com.subtrack.repository.NotificationLogRepository;
import com.subtrack.security.AuthUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Notification history of the logged-in user, newest first. */
@Validated
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationLogRepository logs;

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal AuthUser user,
                                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(logs.findForUser(user.id(), PageRequest.of(page, size)), NotificationResponse::from);
    }
}
