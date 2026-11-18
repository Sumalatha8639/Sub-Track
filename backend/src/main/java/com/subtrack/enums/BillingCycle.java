package com.subtrack.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Billing cycle of a subscription. {@code months} is the cycle length used by the
 * anchor-based due-date calculation (project plan §5.3); ONE_TIME has no next cycle.
 */
public enum BillingCycle {
    MONTHLY(1),
    QUARTERLY(3),
    HALF_YEARLY(6),
    YEARLY(12),
    ONE_TIME(0);

    private final int months;

    BillingCycle(int months) {
        this.months = months;
    }

    public int getMonths() {
        return months;
    }

    public boolean isRecurring() {
        return months > 0;
    }

    /** Default reminder days; short cycles skip the long reminders (project plan §5.3). */
    public List<Integer> defaultReminderDays() {
        return switch (this) {
            case MONTHLY -> List.of(7, 3, 1);
            case QUARTERLY -> List.of(15, 7, 3, 1);
            case HALF_YEARLY, YEARLY, ONE_TIME -> List.of(30, 15, 7, 3, 1);
        };
    }

    /** Amount per month for a recurring cost; ONE_TIME is not a monthly cost. */
    public BigDecimal monthlyAmount(BigDecimal amount) {
        if (!isRecurring()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return amount.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }
}
