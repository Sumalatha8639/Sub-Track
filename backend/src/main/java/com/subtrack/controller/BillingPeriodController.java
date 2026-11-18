package com.subtrack.controller;

import com.subtrack.dto.period.BillingPeriodResponse;
import com.subtrack.dto.period.MarkPeriodRequest;
import com.subtrack.dto.period.MarkPeriodResponse;
import com.subtrack.dto.period.PeriodHistoryResponse;
import com.subtrack.security.AuthUser;
import com.subtrack.service.BillingPeriodService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BillingPeriodController {

    private final BillingPeriodService service;

    /** Payment history of one subscription, newest first. */
    @GetMapping("/api/subscriptions/{subscriptionId}/periods")
    public List<BillingPeriodResponse> list(@AuthenticationPrincipal AuthUser user,
                                            @PathVariable Long subscriptionId) {
        return service.list(user.id(), subscriptionId);
    }

    /** Mark a period PAID or MISSED, or correct an earlier mark. */
    @PatchMapping("/api/subscriptions/{subscriptionId}/periods/{periodId}")
    public MarkPeriodResponse mark(@AuthenticationPrincipal AuthUser user, @PathVariable Long subscriptionId,
                                   @PathVariable Long periodId, @Valid @RequestBody MarkPeriodRequest req) {
        return service.mark(user.id(), subscriptionId, periodId, req);
    }

    @GetMapping("/api/periods/{periodId}/history")
    public List<PeriodHistoryResponse> history(@AuthenticationPrincipal AuthUser user, @PathVariable Long periodId) {
        return service.history(user.id(), periodId);
    }
}
