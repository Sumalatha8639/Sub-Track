package com.subtrack.notification;

import com.subtrack.enums.NotificationType;
import java.util.Collection;
import java.util.Optional;

/**
 * Decides which alert (if any) is due for a PENDING period (project plan §7.2).
 *
 * <p>Catch-up rule: the reminder due is the <b>smallest reminder day ≥ days left</b>. If a run was
 * missed on day 30, the 30-day reminder still goes out on day 28. Larger skipped thresholds are not
 * sent separately, so the user gets one relevant email instead of a burst. The unique key on
 * notification_log makes sure each (period, type, day) is sent once.
 */
public final class ReminderSelector {

    /** days_before value used for the overdue nudge. */
    public static final int OVERDUE_DAYS_BEFORE = -1;

    private ReminderSelector() {
    }

    public record Alert(NotificationType type, int daysBefore) {
    }

    /**
     * @param daysLeft            due date minus today, in the user's timezone
     * @param reminderDays        the subscription's reminder settings (may be empty)
     * @param autoRenew           auto-renew periods get no overdue nudge (they renew by themselves)
     * @param overdueNudgeMaxDays only nudge for periods at most this many days late
     */
    public static Optional<Alert> select(long daysLeft, Collection<Integer> reminderDays, boolean autoRenew,
                                         int overdueNudgeMaxDays) {
        if (daysLeft >= 1) {
            return reminderDays.stream()
                    .filter(d -> d >= daysLeft)
                    .min(Integer::compare)
                    .map(d -> new Alert(NotificationType.REMINDER, d));
        }
        if (daysLeft <= -1 && daysLeft >= -overdueNudgeMaxDays && !autoRenew) {
            return Optional.of(new Alert(NotificationType.OVERDUE, OVERDUE_DAYS_BEFORE));
        }
        return Optional.empty(); // due today, auto-renewed, or too old
    }

    /** Reminder days only matter within this many days of the due date. */
    public static int lookAheadDays(Collection<Integer> reminderDays) {
        return reminderDays.stream().max(Integer::compare).orElse(0);
    }
}
