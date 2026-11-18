package com.subtrack.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.subtrack.entity.BillingPeriod;
import com.subtrack.entity.NotificationLog;
import com.subtrack.entity.ReminderSetting;
import com.subtrack.entity.Subscription;
import com.subtrack.entity.User;
import com.subtrack.enums.BillingCycle;
import com.subtrack.enums.NotificationStatus;
import com.subtrack.enums.NotificationType;
import com.subtrack.enums.PaymentStatus;
import com.subtrack.enums.SubscriptionStatus;
import com.subtrack.repository.BillingPeriodRepository;
import com.subtrack.repository.NotificationLogRepository;
import com.subtrack.repository.UserRepository;
import com.subtrack.support.MutableClock;
import com.subtrack.support.TestProps;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

class NotificationServiceTest {

    /** 03:30 UTC = 09:00 in India. */
    private static final Instant NINE_AM_IST = Instant.parse("2026-11-16T03:30:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-16");

    private final UserRepository users = mock(UserRepository.class);
    private final BillingPeriodRepository periods = mock(BillingPeriodRepository.class);
    private final NotificationLogRepository logs = mock(NotificationLogRepository.class);
    private final NotificationLogWriter writer = mock(NotificationLogWriter.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final MutableClock clock = new MutableClock(NINE_AM_IST);
    private NotificationService service;
    private User user;
    private Subscription sub;

    @BeforeEach
    void setUp() {
        service = new NotificationService(users, periods, logs, writer, emailSender,
                new EmailTemplates(TestProps.create()), TestProps.create(), clock,
                mock(PlatformTransactionManager.class));
        user = new User();
        user.setId(1L);
        user.setName("Asha");
        user.setEmail("asha@example.com");
        user.setTimezone("Asia/Kolkata");
        user.setEmailVerified(true);
        when(users.findByEmailVerifiedTrue()).thenReturn(List.of(user));

        sub = new Subscription();
        sub.setId(10L);
        sub.setUser(user);
        sub.setName("Netflix");
        sub.setBillingCycle(BillingCycle.MONTHLY);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.getReminderSettings().addAll(List.of(new ReminderSetting(sub, 7), new ReminderSetting(sub, 3),
                new ReminderSetting(sub, 1)));
        when(writer.claim(anyLong(), any(), anyInt())).thenReturn(500L);
    }

    @Test
    void sendsTheDueReminderAt9amLocalTime() {
        pendingPeriod(100L, TODAY.plusDays(6));

        NotificationService.RunResult result = service.sendDueReminders();

        assertThat(result.sent()).isEqualTo(1);
        verify(writer).claim(100L, NotificationType.REMINDER, 7);
        ArgumentCaptor<EmailMessage> mail = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(mail.capture());
        assertThat(mail.getValue().to()).isEqualTo("asha@example.com");
        assertThat(mail.getValue().subject()).contains("Netflix").contains("in 6 days");
        verify(writer).markSent(500L, NINE_AM_IST);
    }

    @Test
    void nothingIsSentOutsideTheUsersSendHour() {
        clock.set(Instant.parse("2026-11-16T10:00:00Z")); // 15:30 IST
        pendingPeriod(100L, TODAY.plusDays(6));

        assertThat(service.sendDueReminders().sent()).isZero();
        verify(writer, never()).claim(anyLong(), any(), anyInt());
    }

    @Test
    void alreadyClaimedAlertIsSkippedWithoutSending() {
        pendingPeriod(100L, TODAY.plusDays(3));
        when(writer.claim(100L, NotificationType.REMINDER, 3)).thenThrow(new DataIntegrityViolationException("dup"));

        NotificationService.RunResult result = service.sendDueReminders();

        assertThat(result.skipped()).isEqualTo(1);
        verify(emailSender, never()).send(any());
    }

    @Test
    void failedSendIsRecordedForRetry() {
        pendingPeriod(100L, TODAY.plusDays(1));
        doThrow(new EmailSendException("smtp down", null)).when(emailSender).send(any());
        when(writer.markFailed(eq(500L), any(), any())).thenReturn(NotificationStatus.FAILED);

        assertThat(service.sendDueReminders().failed()).isEqualTo(1);
        verify(writer).markFailed(500L, NINE_AM_IST, "smtp down");
    }

    @Test
    void overdueNudgeForManualRenewal() {
        pendingPeriod(100L, TODAY.minusDays(1));

        service.sendDueReminders();

        verify(writer).claim(100L, NotificationType.OVERDUE, ReminderSelector.OVERDUE_DAYS_BEFORE);
    }

    @Test
    void retryAbandonsWhenPeriodWasMarkedPaidMeanwhile() {
        BillingPeriod period = pendingPeriod(100L, TODAY.plusDays(2));
        period.setPaymentStatus(PaymentStatus.PAID);
        NotificationLog row = failedRow(7L, period);
        when(logs.findRetryableIds(any(), any(), any(), any())).thenReturn(List.of(7L));

        NotificationService.RunResult result = service.retryFailed();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.ABANDONED);
        verify(emailSender, never()).send(any());
    }

    @Test
    void retryResendsWhileStillUseful() {
        BillingPeriod period = pendingPeriod(100L, TODAY.plusDays(2));
        failedRow(7L, period);
        when(logs.findRetryableIds(any(), any(), any(), any())).thenReturn(List.of(7L));

        assertThat(service.retryFailed().sent()).isEqualTo(1);
        verify(writer).markSent(7L, NINE_AM_IST);
    }

    @Test
    void retryAbandonsReminderOnceDueDateHasPassed() {
        BillingPeriod period = pendingPeriod(100L, TODAY.minusDays(1));
        NotificationLog row = failedRow(7L, period);
        when(logs.findRetryableIds(any(), any(), any(), any())).thenReturn(List.of(7L));

        service.retryFailed();

        assertThat(row.getStatus()).isEqualTo(NotificationStatus.ABANDONED);
    }

    private BillingPeriod pendingPeriod(Long id, LocalDate due) {
        BillingPeriod p = new BillingPeriod(sub, due, new BigDecimal("649"));
        p.setId(id);
        when(periods.findPendingForReminders(eq(1L), any(), any(), any(), any())).thenReturn(List.of(p));
        return p;
    }

    private NotificationLog failedRow(Long id, BillingPeriod period) {
        NotificationLog row = new NotificationLog();
        row.setId(id);
        row.setBillingPeriod(period);
        row.setType(NotificationType.REMINDER);
        row.setDaysBefore(3);
        row.setStatus(NotificationStatus.FAILED);
        row.setAttemptCount(1);
        when(logs.findById(id)).thenReturn(Optional.of(row));
        return row;
    }
}
