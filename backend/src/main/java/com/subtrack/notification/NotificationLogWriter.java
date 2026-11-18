package com.subtrack.notification;

import com.subtrack.entity.NotificationLog;
import com.subtrack.enums.NotificationStatus;
import com.subtrack.enums.NotificationType;
import com.subtrack.repository.BillingPeriodRepository;
import com.subtrack.repository.NotificationLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes notification_log rows, each in its own short transaction so a slow email send never
 * holds a database transaction open.
 */
@Component
@RequiredArgsConstructor
public class NotificationLogWriter {

    /** After this many failed attempts the alert is abandoned. */
    public static final int MAX_ATTEMPTS = 5;

    /** Wait before retry number n+1, indexed by failed attempts so far − 1 (project plan §7.3). */
    static final List<Duration> BACKOFF = List.of(
            Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(4), Duration.ofHours(12));

    private final NotificationLogRepository logs;
    private final BillingPeriodRepository periods;

    /**
     * Claims an alert by inserting it as PENDING. If the unique key (period, type, days_before)
     * already exists, this throws DataIntegrityViolationException: someone else owns it, skip it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long claim(Long billingPeriodId, NotificationType type, int daysBefore) {
        NotificationLog log = new NotificationLog();
        log.setBillingPeriod(periods.getReferenceById(billingPeriodId));
        log.setType(type);
        log.setDaysBefore(daysBefore);
        log.setStatus(NotificationStatus.PENDING);
        return logs.saveAndFlush(log).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long id, Instant now) {
        NotificationLog log = logs.findById(id).orElseThrow();
        log.setAttemptCount(log.getAttemptCount() + 1);
        log.setStatus(NotificationStatus.SENT);
        log.setSentAt(now);
        log.setNextRetryAt(null);
        log.setErrorMessage(null);
    }

    /** Records a failed attempt and schedules the next retry, or abandons after MAX_ATTEMPTS. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationStatus markFailed(Long id, Instant now, String error) {
        NotificationLog log = logs.findById(id).orElseThrow();
        int attempts = log.getAttemptCount() + 1;
        log.setAttemptCount(attempts);
        log.setErrorMessage(truncate(error));
        if (attempts >= MAX_ATTEMPTS) {
            log.setStatus(NotificationStatus.ABANDONED);
            log.setNextRetryAt(null);
        } else {
            log.setStatus(NotificationStatus.FAILED);
            log.setNextRetryAt(now.plus(nextDelay(attempts)));
        }
        return log.getStatus();
    }

    static Duration nextDelay(int failedAttempts) {
        return BACKOFF.get(Math.min(failedAttempts, BACKOFF.size()) - 1);
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() <= 1000 ? message : message.substring(0, 997) + "...";
    }
}
