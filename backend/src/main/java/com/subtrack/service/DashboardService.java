package com.subtrack.service;

import com.subtrack.dto.dashboard.DashboardDtos.CategorySlice;
import com.subtrack.dto.dashboard.DashboardDtos.PaidInMonth;
import java.time.YearMonth;
import com.subtrack.dto.dashboard.DashboardDtos.StatusRow;
import com.subtrack.dto.dashboard.DashboardDtos.Summary;
import com.subtrack.dto.dashboard.DashboardDtos.TrendPoint;
import com.subtrack.enums.Category;
import com.subtrack.enums.PeriodDisplayStatus;
import com.subtrack.enums.SubscriptionStatus;
import com.subtrack.repository.BillingPeriodRepository;
import com.subtrack.repository.SubscriptionRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Status dashboard (project plan §3.3). Loads one user's data in two queries and hands it to
 * {@link DashboardCalculator}; a single user has at most a few hundred rows, so this stays fast.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SubscriptionRepository subscriptions;
    private final BillingPeriodRepository periods;
    private final UserTime userTime;

    /** Optional board filters. paymentStatus filters on the row's attention period. */
    public record BoardFilter(PeriodDisplayStatus paymentStatus, SubscriptionStatus status, Category category,
                              Integer dueWithinDays, String sort) {
    }

    @Transactional(readOnly = true)
    public Summary summary(Long userId) {
        return calculator(userId).summary();
    }

    @Transactional(readOnly = true)
    public List<StatusRow> statusBoard(Long userId, BoardFilter f) {
        List<StatusRow> rows = calculator(userId).statusBoard().stream()
                .filter(r -> f.paymentStatus() == null
                        || (r.attention() != null && r.attention().displayStatus() == f.paymentStatus()))
                .filter(r -> f.status() == null || r.status() == f.status())
                .filter(r -> f.category() == null || r.category() == f.category())
                .filter(r -> f.dueWithinDays() == null || r.daysLeft() <= f.dueWithinDays())
                .toList();
        return rows.stream().sorted(comparator(f.sort())).toList();
    }

    @Transactional(readOnly = true)
    public List<CategorySlice> categoryBreakdown(Long userId) {
        return calculator(userId).categoryBreakdown();
    }

    @Transactional(readOnly = true)
    public List<TrendPoint> trend(Long userId, int months) {
        return calculator(userId).trend(months);
    }

    /** Bills paid in {@code month}; null means the current month in the user's timezone. */
    @Transactional(readOnly = true)
    public PaidInMonth paidIn(Long userId, YearMonth month) {
        DashboardCalculator calc = calculator(userId);
        return calc.paidIn(month != null ? month : YearMonth.from(userTime.today(userId)));
    }

    private DashboardCalculator calculator(Long userId) {
        return new DashboardCalculator(
                subscriptions.findByUserIdAndDeletedAtIsNull(userId),
                periods.findAllForUser(userId),
                userTime.today(userId));
    }

    static Comparator<StatusRow> comparator(String sort) {
        if (sort == null) {
            return DashboardCalculator.byUrgency();
        }
        return switch (sort) {
            case "dueDate" -> Comparator.comparing(StatusRow::nextDueDate)
                    .thenComparing(StatusRow::name, String.CASE_INSENSITIVE_ORDER);
            case "amount" -> Comparator.comparing(StatusRow::amount, Comparator.<BigDecimal>reverseOrder())
                    .thenComparing(StatusRow::name, String.CASE_INSENSITIVE_ORDER);
            case "name" -> Comparator.comparing(StatusRow::name, String.CASE_INSENSITIVE_ORDER);
            default -> DashboardCalculator.byUrgency();
        };
    }
}
