package com.subtrack.service;

import com.subtrack.entity.Subscription;
import com.subtrack.enums.SubscriptionStatus;
import com.subtrack.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Renewal job logic (project plan §7.4): auto-renew subscriptions whose due date has passed move
 * to their next cycle. The past periods stay PENDING ("Awaiting confirmation") for the user to mark.
 * Non-auto-renew subscriptions are left alone: they wait for the user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenewalService {

    /** Safety stop so a bad anchor can never loop for ever. */
    static final int MAX_CYCLES_PER_RUN = 120;

    private final SubscriptionRepository subscriptions;
    private final CycleAdvancer advancer;
    private final UserTime userTime;
    private final Clock clock;
    private final PlatformTransactionManager txManager;

    /** Returns how many subscriptions were moved forward. Safe to run any number of times. */
    public int renewOverdueAutoRenewals() {
        // Timezones run up to UTC+14, so "tomorrow in UTC" covers everyone's today
        LocalDate cutoff = LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(1);
        List<Long> ids = subscriptions.findAutoRenewCandidateIds(SubscriptionStatus.ACTIVE, cutoff);

        TransactionTemplate tx = new TransactionTemplate(txManager);
        int renewed = 0;
        for (Long id : ids) {
            try {
                Boolean moved = tx.execute(status -> renewOne(id));
                if (Boolean.TRUE.equals(moved)) {
                    renewed++;
                }
            } catch (RuntimeException e) {
                // One bad row must not stop the others; it will be retried on the next run
                log.error("Auto-renewal failed for subscription {}", id, e);
            }
        }
        if (renewed > 0) {
            log.info("Auto-renewed {} subscription(s)", renewed);
        }
        return renewed;
    }

    private boolean renewOne(Long id) {
        Subscription sub = subscriptions.findById(id).orElse(null);
        if (sub == null || sub.isDeleted() || !sub.isAutoRenew() || sub.getStatus() != SubscriptionStatus.ACTIVE) {
            return false;
        }
        LocalDate today = userTime.today(sub.getUser());
        int steps = 0;
        while (sub.getNextDueDate().isBefore(today) && steps < MAX_CYCLES_PER_RUN) {
            advancer.advance(sub);
            steps++;
        }
        return steps > 0;
    }
}
