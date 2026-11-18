package com.subtrack.controller;

import com.subtrack.dto.common.PageResponse;
import com.subtrack.dto.subscription.ReminderDaysRequest;
import com.subtrack.dto.subscription.StatusChangeRequest;
import com.subtrack.dto.subscription.SubscriptionRequest;
import com.subtrack.dto.subscription.SubscriptionResponse;
import com.subtrack.enums.Category;
import com.subtrack.enums.SubscriptionStatus;
import com.subtrack.security.AuthUser;
import com.subtrack.service.SubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService service;

    @GetMapping
    public PageResponse<SubscriptionResponse> list(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) @Min(0) @Max(366) Integer dueWithinDays,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "nextDueDate") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return service.list(user.id(), new SubscriptionService.Filter(category, status, dueWithinDays, q),
                page, size, sort, direction);
    }

    @GetMapping("/{id}")
    public SubscriptionResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.get(user.id(), id);
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(@AuthenticationPrincipal AuthUser user,
                                                       @Valid @RequestBody SubscriptionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.id(), req));
    }

    @PutMapping("/{id}")
    public SubscriptionResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                       @Valid @RequestBody SubscriptionRequest req) {
        return service.update(user.id(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        service.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public SubscriptionResponse changeStatus(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                             @Valid @RequestBody StatusChangeRequest req) {
        return service.changeStatus(user.id(), id, req.status());
    }

    @PutMapping("/{id}/reminders")
    public SubscriptionResponse updateReminders(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                                @Valid @RequestBody ReminderDaysRequest req) {
        return service.updateReminders(user.id(), id, req.days());
    }
}
