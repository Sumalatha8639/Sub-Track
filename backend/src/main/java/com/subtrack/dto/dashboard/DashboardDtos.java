package com.subtrack.dto.dashboard;

import com.subtrack.dto.period.BillingPeriodResponse;
import com.subtrack.enums.BillingCycle;
import com.subtrack.enums.Category;
import com.subtrack.enums.PaymentStatus;
import com.subtrack.enums.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Response shapes for /api/dashboard (project plan §3.3). All amounts in INR. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** The summary tiles. "This month" is the calendar month in the user's timezone. */
    public record Summary(
            int activeCount,
            int dueIn30DaysCount,
            BigDecimal dueIn30DaysAmount,
            int overdueCount,
            BigDecimal overdueAmount,
            int awaitingConfirmationCount,
            int paidThisMonthCount,
            BigDecimal paidThisMonthAmount,
            int missedThisMonthCount,
            BigDecimal monthlyCommitment,
            BigDecimal yearlyCommitment) {
    }

    /** Most recent period that was marked PAID or MISSED. */
    public record LastClosed(LocalDate dueDate, PaymentStatus paymentStatus, LocalDate paidOn, BigDecimal amountPaid) {
    }

    /**
     * One row per subscription. {@code attention} is the period the user should act on: the oldest
     * unmarked period if it is past due, otherwise the current one. Null for cancelled or expired
     * subscriptions, which need no action.
     */
    public record StatusRow(
            Long subscriptionId,
            String name,
            Category category,
            BigDecimal amount,
            BillingCycle billingCycle,
            LocalDate nextDueDate,
            long daysLeft,
            SubscriptionStatus status,
            boolean autoRenew,
            BillingPeriodResponse attention,
            int unmarkedPastCount,
            LastClosed lastClosed) {
    }

    public record CategorySlice(Category category, int count, BigDecimal monthlyAmount) {
    }

    /** One bill paid in the selected month (by paid-on date). */
    public record PaidItem(
            Long subscriptionId,
            String name,
            Category category,
            BillingCycle billingCycle,
            SubscriptionStatus subscriptionStatus,
            BillingPeriodResponse period) {
    }

    /** Bills paid in one calendar month, newest payment first, with the month's total. */
    public record PaidInMonth(String month, BigDecimal total, int count, java.util.List<PaidItem> items) {
    }

    /** One calendar month: money actually paid (by paid-on date) and periods missed (by due date). */
    public record TrendPoint(String month, BigDecimal paidAmount, int paidCount, int missedCount) {
    }
}
