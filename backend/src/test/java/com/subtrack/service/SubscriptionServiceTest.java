package com.subtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.subtrack.dto.subscription.SubscriptionRequest;
import com.subtrack.dto.subscription.SubscriptionResponse;
import com.subtrack.entity.BillingPeriod;
import com.subtrack.entity.Subscription;
import com.subtrack.entity.User;
import com.subtrack.enums.BillingCycle;
import com.subtrack.enums.Category;
import com.subtrack.enums.PaymentStatus;
import com.subtrack.enums.SubscriptionStatus;
import com.subtrack.exception.BadRequestException;
import com.subtrack.exception.NotFoundException;
import com.subtrack.repository.BillingPeriodRepository;
import com.subtrack.repository.SubscriptionRepository;
import com.subtrack.repository.UserRepository;
import com.subtrack.support.MutableClock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SubscriptionServiceTest {

    /** 20:00 UTC on 16 Nov = 01:30 on 17 Nov in India: "today" must be the 17th for an IST user. */
    private static final Instant NOW = Instant.parse("2026-11-16T20:00:00Z");
    private static final LocalDate TODAY_IST = LocalDate.parse("2026-11-17");

    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final BillingPeriodRepository periods = mock(BillingPeriodRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MutableClock clock = new MutableClock(NOW);
    private SubscriptionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptions, periods, users, new UserTime(users, clock), clock);
        user = new User();
        user.setId(1L);
        user.setTimezone("Asia/Kolkata");
        when(users.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void createAnchorsOnDueDateAppliesCycleDefaultsAndOpensFirstPeriod() {
        SubscriptionResponse res = service.create(1L, request(BillingCycle.MONTHLY, "2026-12-05", false, null));

        assertThat(res.nextDueDate()).isEqualTo(LocalDate.parse("2026-12-05"));
        assertThat(res.anchorDate()).isEqualTo(LocalDate.parse("2026-12-05"));
        assertThat(res.daysLeft()).isEqualTo(18); // from 17 Nov (IST), not 16 Nov (UTC)
        assertThat(res.reminderDays()).containsExactly(7, 3, 1);
        assertThat(res.monthlyAmount()).isEqualByComparingTo("649.00");
        assertThat(res.status()).isEqualTo(SubscriptionStatus.ACTIVE);

        ArgumentCaptor<BillingPeriod> period = ArgumentCaptor.forClass(BillingPeriod.class);
        verify(periods).save(period.capture());
        assertThat(period.getValue().getDueDate()).isEqualTo(LocalDate.parse("2026-12-05"));
        assertThat(period.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(period.getValue().getAmountDue()).isEqualByComparingTo("649");
    }

    @Test
    void createUsesCustomReminderDaysDeduplicated() {
        SubscriptionResponse res = service.create(1L,
                request(BillingCycle.YEARLY, "2027-03-01", false, List.of(60, 7, 7, 1)));

        assertThat(res.reminderDays()).containsExactly(60, 7, 1);
    }

    @Test
    void oneTimeCannotAutoRenew() {
        assertThatThrownBy(() -> service.create(1L, request(BillingCycle.ONE_TIME, "2026-12-05", true, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void dueDateMoreThanAYearAgoIsRejected() {
        assertThatThrownBy(() -> service.create(1L, request(BillingCycle.MONTHLY, "2025-11-01", false, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void otherUsersSubscriptionIsNotFound() {
        when(subscriptions.findByIdAndUserIdAndDeletedAtIsNull(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L, 99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void statusTransitions() {
        Subscription sub = existing(SubscriptionStatus.ACTIVE);

        assertThat(service.changeStatus(1L, 10L, SubscriptionStatus.PAUSED).status())
                .isEqualTo(SubscriptionStatus.PAUSED);
        assertThat(service.changeStatus(1L, 10L, SubscriptionStatus.ACTIVE).status())
                .isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(service.changeStatus(1L, 10L, SubscriptionStatus.CANCELLED).status())
                .isEqualTo(SubscriptionStatus.CANCELLED);
        assertThatThrownBy(() -> service.changeStatus(1L, 10L, SubscriptionStatus.PAUSED))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.changeStatus(1L, 10L, SubscriptionStatus.EXPIRED))
                .isInstanceOf(BadRequestException.class);

        sub.setStatus(SubscriptionStatus.EXPIRED);
        assertThatThrownBy(() -> service.changeStatus(1L, 10L, SubscriptionStatus.ACTIVE))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateWithNewDueDateReanchorsAndMovesPendingPeriod() {
        Subscription sub = existing(SubscriptionStatus.ACTIVE);
        sub.setCyclesElapsed(3);
        BillingPeriod current = new BillingPeriod(sub, sub.getNextDueDate(), sub.getAmount());
        when(periods.findBySubscriptionIdAndDueDate(10L, sub.getNextDueDate())).thenReturn(Optional.of(current));

        SubscriptionRequest req = new SubscriptionRequest("Netflix", Category.OTT, new BigDecimal("799"),
                BillingCycle.MONTHLY, LocalDate.parse("2026-12-10"), false, null, null);
        SubscriptionResponse res = service.update(1L, 10L, req);

        assertThat(res.nextDueDate()).isEqualTo(LocalDate.parse("2026-12-10"));
        assertThat(sub.getStartDate()).isEqualTo(LocalDate.parse("2026-12-10"));
        assertThat(sub.getCyclesElapsed()).isZero();
        assertThat(current.getDueDate()).isEqualTo(LocalDate.parse("2026-12-10"));
        assertThat(current.getAmountDue()).isEqualByComparingTo("799");
    }

    @Test
    void updateRemindersReplacesDays() {
        existing(SubscriptionStatus.ACTIVE);

        assertThat(service.updateReminders(1L, 10L, List.of(10, 2)).reminderDays()).containsExactly(10, 2);
        assertThat(service.updateReminders(1L, 10L, List.of()).reminderDays()).isEmpty();
    }

    @Test
    void deleteIsSoft() {
        Subscription sub = existing(SubscriptionStatus.ACTIVE);

        service.delete(1L, 10L);

        assertThat(sub.getDeletedAt()).isEqualTo(NOW);
    }

    // ---- helpers -------------------------------------------------------------

    private Subscription existing(SubscriptionStatus status) {
        Subscription sub = new Subscription();
        sub.setId(10L);
        sub.setUser(user);
        sub.setName("Netflix");
        sub.setCategory(Category.OTT);
        sub.setAmount(new BigDecimal("649"));
        sub.setBillingCycle(BillingCycle.MONTHLY);
        sub.setStartDate(LocalDate.parse("2026-08-05"));
        sub.setNextDueDate(LocalDate.parse("2026-12-05"));
        sub.setStatus(status);
        when(subscriptions.findByIdAndUserIdAndDeletedAtIsNull(10L, 1L)).thenReturn(Optional.of(sub));
        when(periods.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return sub;
    }

    private static SubscriptionRequest request(BillingCycle cycle, String due, boolean autoRenew, List<Integer> days) {
        return new SubscriptionRequest("Netflix", Category.OTT, new BigDecimal("649"), cycle,
                LocalDate.parse(due), autoRenew, null, days);
    }
}
