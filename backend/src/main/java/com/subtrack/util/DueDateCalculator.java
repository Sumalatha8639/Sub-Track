package com.subtrack.util;

import com.subtrack.enums.BillingCycle;
import java.time.LocalDate;

/**
 * Anchor-based due dates (project plan §5.3).
 *
 * <p>The anchor is the first tracked due date. Due date number {@code n} (0-based) is
 * {@code anchor + n × cycle}, always computed from the anchor, so month-end dates never drift:
 * an anchor of 31 Jan gives 28/29 Feb, 31 Mar, 30 Apr, 31 May ...
 */
public final class DueDateCalculator {

    private DueDateCalculator() {
    }

    /** The {@code cyclesElapsed}-th due date after the anchor (0 = the anchor itself). */
    public static LocalDate dueDate(LocalDate anchor, BillingCycle cycle, int cyclesElapsed) {
        if (cyclesElapsed < 0) {
            throw new IllegalArgumentException("cyclesElapsed must be >= 0");
        }
        if (cyclesElapsed == 0) {
            return anchor;
        }
        if (!cycle.isRecurring()) {
            throw new IllegalArgumentException("ONE_TIME subscriptions have a single due date");
        }
        return anchor.plusMonths((long) cycle.getMonths() * cyclesElapsed);
    }

    /**
     * Smallest number of cycles whose due date is on or after {@code date}. Used by the renewal job
     * to skip over several missed cycles at once.
     */
    public static int cyclesUntilOnOrAfter(LocalDate anchor, BillingCycle cycle, LocalDate date) {
        if (!date.isAfter(anchor)) {
            return 0;
        }
        if (!cycle.isRecurring()) {
            throw new IllegalArgumentException("ONE_TIME subscriptions have a single due date");
        }
        // Estimate from whole months, then correct for day-of-month clamping
        long months = java.time.temporal.ChronoUnit.MONTHS.between(anchor, date);
        int n = (int) Math.max(0, months / cycle.getMonths());
        while (dueDate(anchor, cycle, n).isBefore(date)) {
            n++;
        }
        while (n > 0 && !dueDate(anchor, cycle, n - 1).isBefore(date)) {
            n--;
        }
        return n;
    }
}
