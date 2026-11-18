package com.subtrack.notification;

import com.subtrack.config.AppProperties;
import com.subtrack.entity.BillingPeriod;
import com.subtrack.entity.NotificationLog;
import com.subtrack.entity.ReminderSetting;
import com.subtrack.entity.Subscription;
import com.subtrack.entity.User;
import com.subtrack.enums.NotificationStatus;
import com.subtrack.enums.NotificationType;
import com.subtrack.enums.PaymentStatus;
import com.subtrack.enums.SubscriptionStatus;
import com.subtrack.notification.ReminderSelector.Alert;
import com.subtrack.repository.BillingPeriodRepository;
import com.subtrack.repository.NotificationLogRepository;
import com.subtrack.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reminder job (project plan §7.2) and retry job (§7.3).
 *
 * <p>Flow per alert: read in a short transaction → claim by inserting a PENDING log row (unique key
 * decides who sends) → send the email outside any transaction → record SENT or FAILED.
 */
@Slf4j
@Service
public class NotificationService {

    /** A PENDING row older than this was claimed by a run that crashed or was put to sleep. */
    static final Duration STALE_CLAIM = Duration.ofMinutes(15);

    private final UserRepository users;
    private final BillingPeriodRepository periods;
    private final NotificationLogRepository logs;
    private final NotificationLogWriter writer;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final AppProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public NotificationService(UserRepository users, BillingPeriodRepository periods, NotificationLogRepository logs,
                               NotificationLogWriter writer, EmailSender emailSender, EmailTemplates templates,
                               AppProperties props, Clock clock, PlatformTransactionManager txManager) {
        this.users = users;
        this.periods = periods;
        this.logs = logs;
        this.writer = writer;
        this.emailSender = emailSender;
        this.templates = templates;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Counts for one job run. */
    public record RunResult(int sent, int failed, int skipped) {

        static RunResult empty() {
            return new RunResult(0, 0, 0);
        }
    }

    private record Planned(Long periodId, Alert alert, EmailMessage message) {
    }

    // ---- reminder job ----------------------------------------------------------

    public RunResult sendDueReminders() {
        Instant now = clock.instant();
        int sent = 0;
        int failed = 0;
        int skipped = 0;
        for (User user : users.findByEmailVerifiedTrue()) {
            ZonedDateTime local = now.atZone(ZoneId.of(user.getTimezone()));
            int sendHour = props.reminders().sendHour();
            if (sendHour >= 0 && local.getHour() != sendHour) {
                continue;
            }
            List<Planned> planned = tx.execute(s -> plan(user, local.toLocalDate()));
            for (Planned p : planned) {
                switch (deliver(p)) {
                    case SENT -> sent++;
                    case FAILED, ABANDONED -> failed++;
                    default -> skipped++;
                }
            }
        }
        if (sent + failed > 0) {
            log.info("Reminder run: {} sent, {} failed, {} already sent", sent, failed, skipped);
        }
        return new RunResult(sent, failed, skipped);
    }

    private List<Planned> plan(User user, LocalDate today) {
        int nudgeMax = props.reminders().overdueNudgeMaxDays();
        List<BillingPeriod> pending = periods.findPendingForReminders(user.getId(), SubscriptionStatus.ACTIVE,
                PaymentStatus.PENDING, today.minusDays(nudgeMax), today.plusDays(90));
        List<Planned> planned = new ArrayList<>();
        for (BillingPeriod period : pending) {
            Subscription sub = period.getSubscription();
            List<Integer> days = sub.getReminderSettings().stream().map(ReminderSetting::getDaysBefore).toList();
            long daysLeft = ChronoUnit.DAYS.between(today, period.getDueDate());
            ReminderSelector.select(daysLeft, days, sub.isAutoRenew(), nudgeMax).ifPresent(alert ->
                    planned.add(new Planned(period.getId(), alert, buildMessage(alert.type(), user, sub, period, daysLeft))));
        }
        return planned;
    }

    /** Claim, send, record. Returns PENDING when the alert was already claimed (skipped). */
    private NotificationStatus deliver(Planned p) {
        Long logId;
        try {
            logId = writer.claim(p.periodId(), p.alert().type(), p.alert().daysBefore());
        } catch (DataIntegrityViolationException alreadyClaimed) {
            return NotificationStatus.PENDING;
        }
        return send(logId, p.message());
    }

    // ---- retry job ---------------------------------------------------------------

    public RunResult retryFailed() {
        Instant now = clock.instant();
        List<Long> ids = logs.findRetryableIds(NotificationStatus.FAILED, NotificationStatus.PENDING,
                now, now.minus(STALE_CLAIM));
        int sent = 0;
        int failed = 0;
        int abandoned = 0;
        for (Long id : ids) {
            Optional<EmailMessage> message = tx.execute(s -> prepareRetry(id));
            if (message.isEmpty()) {
                abandoned++;
                continue;
            }
            if (send(id, message.get()) == NotificationStatus.SENT) {
                sent++;
            } else {
                failed++;
            }
        }
        if (!ids.isEmpty()) {
            log.info("Retry run: {} sent, {} failed again, {} abandoned", sent, failed, abandoned);
        }
        return new RunResult(sent, failed, abandoned);
    }

    /**
     * Rebuilds the email for a retry, or abandons the row when the alert is no longer useful:
     * the period was marked, the subscription is no longer active, or the moment has passed.
     */
    private Optional<EmailMessage> prepareRetry(Long id) {
        NotificationLog row = logs.findById(id).orElse(null);
        if (row == null || (row.getStatus() != NotificationStatus.FAILED && row.getStatus() != NotificationStatus.PENDING)) {
            return Optional.empty();
        }
        BillingPeriod period = row.getBillingPeriod();
        Subscription sub = period.getSubscription();
        User user = sub.getUser();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
        long daysLeft = ChronoUnit.DAYS.between(today, period.getDueDate());

        boolean useless = period.getPaymentStatus() != PaymentStatus.PENDING
                || sub.getStatus() != SubscriptionStatus.ACTIVE
                || sub.isDeleted()
                || !user.isEmailVerified()
                || (row.getType() == NotificationType.REMINDER && daysLeft < 0)
                || (row.getType() == NotificationType.OVERDUE && -daysLeft > props.reminders().overdueNudgeMaxDays());
        if (useless) {
            row.setStatus(NotificationStatus.ABANDONED);
            row.setNextRetryAt(null);
            return Optional.empty();
        }
        return Optional.of(buildMessage(row.getType(), user, sub, period, daysLeft));
    }

    // ---- shared --------------------------------------------------------------------

    private NotificationStatus send(Long logId, EmailMessage message) {
        try {
            emailSender.send(message);
            writer.markSent(logId, clock.instant());
            return NotificationStatus.SENT;
        } catch (RuntimeException e) {
            log.warn("Email for notification {} failed: {}", logId, e.getMessage());
            return writer.markFailed(logId, clock.instant(), e.getMessage());
        }
    }

    private EmailMessage buildMessage(NotificationType type, User user, Subscription sub, BillingPeriod period,
                                      long daysLeft) {
        return type == NotificationType.REMINDER
                ? templates.reminder(user.getEmail(), user.getName(), sub.getName(), period.getAmountDue(),
                        period.getDueDate(), daysLeft, sub.getId())
                : templates.overdue(user.getEmail(), user.getName(), sub.getName(), period.getAmountDue(),
                        period.getDueDate(), sub.getId());
    }
}
