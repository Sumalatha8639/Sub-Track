package com.subtrack.service;

import com.subtrack.entity.BillingPeriod;
import com.subtrack.entity.Subscription;
import com.subtrack.enums.SubscriptionStatus;
import com.subtrack.repository.BillingPeriodRepository;
import com.subtrack.util.DueDateCalculator;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Moves a subscription to its next cycle; shared by "mark paid/missed" and the renewal job. */
@Component
@RequiredArgsConstructor
public class CycleAdvancer {

    private final BillingPeriodRepository periods;

    /**
     * Advances one cycle and opens the next PENDING period. A ONE_TIME subscription has no next
     * cycle, so it becomes EXPIRED instead. Returns the new current period, or null if expired.
     */
    public BillingPeriod advance(Subscription sub) {
        if (!sub.getBillingCycle().isRecurring()) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            return null;
        }
        sub.setCyclesElapsed(sub.getCyclesElapsed() + 1);
        LocalDate next = DueDateCalculator.dueDate(sub.getStartDate(), sub.getBillingCycle(), sub.getCyclesElapsed());
        sub.setNextDueDate(next);
        return ensurePeriod(sub, next);
    }

    /** Idempotent: returns the existing period for that due date, or creates a PENDING one. */
    public BillingPeriod ensurePeriod(Subscription sub, LocalDate dueDate) {
        return periods.findBySubscriptionIdAndDueDate(sub.getId(), dueDate)
                .orElseGet(() -> periods.save(new BillingPeriod(sub, dueDate, sub.getAmount())));
    }
}
