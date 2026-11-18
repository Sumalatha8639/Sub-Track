package com.subtrack.controller;

import com.subtrack.dto.dashboard.DashboardDtos.CategorySlice;
import com.subtrack.dto.dashboard.DashboardDtos.PaidInMonth;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import com.subtrack.dto.dashboard.DashboardDtos.StatusRow;
import com.subtrack.dto.dashboard.DashboardDtos.Summary;
import com.subtrack.dto.dashboard.DashboardDtos.TrendPoint;
import com.subtrack.enums.Category;
import com.subtrack.enums.PeriodDisplayStatus;
import com.subtrack.enums.SubscriptionStatus;
import com.subtrack.security.AuthUser;
import com.subtrack.service.DashboardService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/summary")
    public Summary summary(@AuthenticationPrincipal AuthUser user) {
        return service.summary(user.id());
    }

    /** Every subscription with its payment status. Default sort: most urgent first. */
    @GetMapping("/status-board")
    public List<StatusRow> statusBoard(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) PeriodDisplayStatus paymentStatus,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) @Min(0) @Max(366) Integer dueWithinDays,
            @RequestParam(required = false) String sort) {
        return service.statusBoard(user.id(),
                new DashboardService.BoardFilter(paymentStatus, status, category, dueWithinDays, sort));
    }

    @GetMapping("/category-breakdown")
    public List<CategorySlice> categoryBreakdown(@AuthenticationPrincipal AuthUser user) {
        return service.categoryBreakdown(user.id());
    }

    @GetMapping("/trend")
    public List<TrendPoint> trend(@AuthenticationPrincipal AuthUser user,
                                  @RequestParam(defaultValue = "12") @Min(1) @Max(36) int months) {
        return service.trend(user.id(), months);
    }

    /** Bills paid in a month, e.g. ?month=2026-11 (default: this month in the user's timezone). */
    @GetMapping("/paid")
    public PaidInMonth paid(@AuthenticationPrincipal AuthUser user,
                            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.paidIn(user.id(), month);
    }

    /** Renewals in the next N days (project plan §6), soonest first. */
    @GetMapping("/upcoming")
    public List<StatusRow> upcoming(@AuthenticationPrincipal AuthUser user,
                                    @RequestParam(defaultValue = "30") @Min(0) @Max(366) int days) {
        return service.statusBoard(user.id(),
                new DashboardService.BoardFilter(null, SubscriptionStatus.ACTIVE, null, days, "dueDate"))
                .stream().filter(r -> r.daysLeft() >= 0).toList();
    }
}
