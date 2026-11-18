package com.subtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.subtrack.entity.BillingPeriod;
import com.subtrack.entity.Subscription;
import com.subtrack.entity.User;
import com.subtrack.enums.BillingCycle;
import com.subtrack.enums.SubscriptionStatus;
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
import org.springframework.transaction.PlatformTransactionManager;

class RenewalServiceTest {

    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final BillingPeriodRepository periods = mock(BillingPeriodRepository.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T06:00:00Z"));
    private RenewalService service;

    @BeforeEach
    void setUp() {
        service = new RenewalService(subscriptions, new CycleAdvancer(periods),
                new UserTime(mock(UserRepository.class), clock), clock, mock(PlatformTransactionManager.class));
        when(periods.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(periods.findBySubscriptionIdAndDueDate(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void autoRenewCatchesUpSeveralMissedCyclesAndOpensAPeriodForEach() {
        // Anchor 31 Jul; due dates 31 Aug, 30 Sep, 31 Oct have passed; next is 30 Nov
        Subscription sub = autoRenew(1L, "2026-07-31", 1, "2026-08-31");
        when(subscriptions.findAutoRenewCandidateIds(eq(SubscriptionStatus.ACTIVE), any())).thenReturn(List.of(1L));

        int renewed = service.renewOverdueAutoRenewals();

        assertThat(renewed).isEqualTo(1);
        assertThat(sub.getNextDueDate()).isEqualTo(LocalDate.parse("2026-11-30"));
        assertThat(sub.getCyclesElapsed()).isEqualTo(4);
        ArgumentCaptor<BillingPeriod> created = ArgumentCaptor.forClass(BillingPeriod.class);
        verify(periods, times(3)).save(created.capture());
        assertThat(created.getAllValues()).extracting(BillingPeriod::getDueDate).containsExactly(
                LocalDate.parse("2026-09-30"), LocalDate.parse("2026-10-31"), LocalDate.parse("2026-11-30"));
    }

    @Test
    void dueTodayIsNotRenewedYet() {
        Subscription sub = autoRenew(2L, "2026-11-16", 0, "2026-11-16");
        when(subscriptions.findAutoRenewCandidateIds(eq(SubscriptionStatus.ACTIVE), any())).thenReturn(List.of(2L));

        assertThat(service.renewOverdueAutoRenewals()).isZero();
        assertThat(sub.getNextDueDate()).isEqualTo(LocalDate.parse("2026-11-16"));
    }

    @Test
    void pausedOrDeletedSubscriptionsAreSkipped() {
        Subscription paused = autoRenew(3L, "2026-10-01", 0, "2026-10-01");
        paused.setStatus(SubscriptionStatus.PAUSED);
        Subscription deleted = autoRenew(4L, "2026-10-01", 0, "2026-10-01");
        deleted.setDeletedAt(Instant.parse("2026-11-01T00:00:00Z"));
        when(subscriptions.findAutoRenewCandidateIds(eq(SubscriptionStatus.ACTIVE), any())).thenReturn(List.of(3L, 4L));

        assertThat(service.renewOverdueAutoRenewals()).isZero();
        assertThat(paused.getNextDueDate()).isEqualTo(LocalDate.parse("2026-10-01"));
    }

    private Subscription autoRenew(Long id, String anchor, int cycles, String nextDue) {
        User user = new User();
        user.setTimezone("Asia/Kolkata");
        Subscription s = new Subscription();
        s.setId(id);
        s.setUser(user);
        s.setAmount(new BigDecimal("299"));
        s.setBillingCycle(BillingCycle.MONTHLY);
        s.setStartDate(LocalDate.parse(anchor));
        s.setCyclesElapsed(cycles);
        s.setNextDueDate(LocalDate.parse(nextDue));
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setAutoRenew(true);
        when(subscriptions.findById(id)).thenReturn(Optional.of(s));
        return s;
    }
}
