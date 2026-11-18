package com.subtrack.enums;

import com.subtrack.entity.BillingPeriod;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * What the user sees for a billing period (project plan §3.3). Computed from the stored
 * PaymentStatus, the due date, today (user's timezone) and the auto-renew flag; never stored.
 */
public enum PeriodDisplayStatus {
    PAID,
    MISSED,
    /** Pending, due in more than 7 days. */
    UPCOMING,
    /** Pending, due in 1–7 days. */
    DUE_SOON,
    DUE_TODAY,
    /** Pending, past due, not auto-renew: the user still has to act. */
    OVERDUE,
    /** Pending, past due, auto-renew: probably charged automatically, please confirm. */
    AWAITING_CONFIRMATION;

    public static final int DUE_SOON_DAYS = 7;

    public static PeriodDisplayStatus of(PaymentStatus status, LocalDate dueDate, boolean autoRenew, LocalDate today) {
        return switch (status) {
            case PAID -> PAID;
            case MISSED -> MISSED;
            case PENDING -> {
                long daysLeft = ChronoUnit.DAYS.between(today, dueDate);
                if (daysLeft > DUE_SOON_DAYS) {
                    yield UPCOMING;
                } else if (daysLeft > 0) {
                    yield DUE_SOON;
                } else if (daysLeft == 0) {
                    yield DUE_TODAY;
                } else {
                    yield autoRenew ? AWAITING_CONFIRMATION : OVERDUE;
                }
            }
        };
    }

    public static PeriodDisplayStatus of(BillingPeriod period, LocalDate today) {
        return of(period.getPaymentStatus(), period.getDueDate(), period.getSubscription().isAutoRenew(), today);
    }
}
