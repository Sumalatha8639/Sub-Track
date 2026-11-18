package com.subtrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.subtrack.dto.period.MarkPeriodRequest;
import com.subtrack.dto.period.MarkPeriodResponse;
import com.subtrack.entity.BillingPeriod;
import com.subtrack.entity.BillingPeriodHistory;
import com.subtrack.entity.Subscription;
import com.subtrack.entity.User;
import com.subtrack.enums.BillingCycle;
import com.subtrack.enums.Category;
import com.subtrack.enums.PaymentStatus;
import com.subtrack.enums.PeriodDisplayStatus;
import com.subtrack.enums.SubscriptionStatus;
import com.subtrack.exception.BadRequestException;
import com.subtrack.exception.NotFoundException;
import com.subtrack.repository.BillingPeriodHistoryRepository;
import com.subtrack.repository.BillingPeriodRepository;
import com.subtrack.repository.UserRepository;
import com.subtrack.support.MutableClock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BillingPeriodServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-16");
    private static final Instant NOW = Instant.parse("2026-11-16T06:00:00Z"); // 11:30 IST

    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final BillingPeriodRepository periods = mock(BillingPeriodRepository.class);
    private final BillingPeriodHistoryRepository history = mock(BillingPeriodHistoryRepository.class);
    private final MutableClock clock = new MutableClock(NOW);
    private BillingPeriodService service;
    private Subscription sub;

    @BeforeEach
    void setUp() {
        UserTime userTime = new UserTime(mock(UserRepository.class), clock);
        service = new BillingPeriodService(subscriptionService, periods, history, new CycleAdvancer(periods),
                userTime, clock);

        User user = new User();
        user.setId(1L);
        user.setTimezone("Asia/Kolkata");
        sub = new Subscription();
        sub.setId(10L);
        sub.setUser(user);
        sub.setName("Netflix");
        sub.setCategory(Category.OTT);
        sub.setAmount(new BigDecimal("649.00"));
        sub.setBillingCycle(BillingCycle.MONTHLY);
        sub.setStartDate(LocalDate.parse("2026-09-30"));
        sub.setCyclesElapsed(2);
        sub.setNextDueDate(LocalDate.parse("2026-11-30"));
        sub.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionService.load(1L, 10L)).thenReturn(sub);
        when(periods.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(periods.findBySubscriptionIdAndDueDate(eq(10L), any())).thenReturn(Optional.empty());
    }

    @Test
    void payingCurrentPeriodEarlyAdvancesToNextCycle() {
        BillingPeriod current = period(100L, "2026-11-30", PaymentStatus.PENDING);

        MarkPeriodResponse res = service.mark(1L, 10L, 100L, new MarkPeriodRequest(PaymentStatus.PAID, null, null, null));

        assertThat(current.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(current.getPaidOn()).isEqualTo(TODAY);
        assertThat(current.getAmountPaid()).isEqualByComparingTo("649");
        assertThat(current.getMarkedAt()).isEqualTo(NOW);
        assertThat(res.period().displayStatus()).isEqualTo(PeriodDisplayStatus.PAID);

        // Anchor 30 Sep + 3 months = 30 Dec (no drift)
        assertThat(sub.getCyclesElapsed()).isEqualTo(3);
        assertThat(sub.getNextDueDate()).isEqualTo(LocalDate.parse("2026-12-30"));
        assertThat(res.subscription().nextDueDate()).isEqualTo(LocalDate.parse("2026-12-30"));
        ArgumentCaptor<BillingPeriod> created = ArgumentCaptor.forClass(BillingPeriod.class);
        verify(periods).save(created.capture());
        assertThat(created.getValue().getDueDate()).isEqualTo(LocalDate.parse("2026-12-30"));
        assertThat(created.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);

        ArgumentCaptor<BillingPeriodHistory> h = ArgumentCaptor.forClass(BillingPeriodHistory.class);
        verify(history).save(h.capture());
        assertThat(h.getValue().getOldStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(h.getValue().getNewStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void customPaidOnAndAmountAreKept() {
        sub.setNextDueDate(LocalDate.parse("2026-11-10"));
        BillingPeriod current = period(100L, "2026-11-10", PaymentStatus.PENDING);

        service.mark(1L, 10L, 100L, new MarkPeriodRequest(PaymentStatus.PAID, LocalDate.parse("2026-11-12"),
                new BigDecimal("699.00"), "Price went up"));

        assertThat(current.getPaidOn()).isEqualTo(LocalDate.parse("2026-11-12"));
        assertThat(current.getAmountPaid()).isEqualByComparingTo("699");
        assertThat(current.getNote()).isEqualTo("Price went up");
    }

    @Test
    void missedBeforeDueDateIsRejected() {
        period(100L, "2026-11-30", PaymentStatus.PENDING);

        assertThatThrownBy(() -> service.mark(1L, 10L, 100L,
                new MarkPeriodRequest(PaymentStatus.MISSED, null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void missedOnOverduePeriodAdvancesAndClearsPaymentFields() {
        sub.setNextDueDate(LocalDate.parse("2026-10-30"));
        BillingPeriod overdue = period(100L, "2026-10-30", PaymentStatus.PENDING);

        service.mark(1L, 10L, 100L, new MarkPeriodRequest(PaymentStatus.MISSED, TODAY, new BigDecimal("1"), "lapsed"));

        assertThat(overdue.getPaymentStatus()).isEqualTo(PaymentStatus.MISSED);
        assertThat(overdue.getPaidOn()).isNull();
        assertThat(overdue.getAmountPaid()).isNull();
        assertThat(sub.getNextDueDate()).isEqualTo(LocalDate.parse("2026-12-30"));
    }

    @Test
    void futurePaidOnOrPendingTargetIsRejected() {
        period(100L, "2026-11-30", PaymentStatus.PENDING);

        assertThatThrownBy(() -> service.mark(1L, 10L, 100L,
                new MarkPeriodRequest(PaymentStatus.PAID, TODAY.plusDays(1), null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.mark(1L, 10L, 100L,
                new MarkPeriodRequest(PaymentStatus.PENDING, null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void correctionDoesNotMoveDatesButIsAudited() {
        BillingPeriod old = period(90L, "2026-10-30", PaymentStatus.PAID);
        old.setPaidOn(LocalDate.parse("2026-10-29"));

        service.mark(1L, 10L, 90L, new MarkPeriodRequest(PaymentStatus.MISSED, null, null, null));

        assertThat(old.getPaymentStatus()).isEqualTo(PaymentStatus.MISSED);
        assertThat(sub.getNextDueDate()).isEqualTo(LocalDate.parse("2026-11-30"));
        assertThat(sub.getCyclesElapsed()).isEqualTo(2);
        verify(periods, never()).save(any());
        verify(history).save(any(BillingPeriodHistory.class));
    }

    @Test
    void editingAPaidPeriodWithoutStatusChangeAddsNoHistory() {
        BillingPeriod old = period(90L, "2026-10-30", PaymentStatus.PAID);

        service.mark(1L, 10L, 90L, new MarkPeriodRequest(PaymentStatus.PAID, LocalDate.parse("2026-10-28"),
                new BigDecimal("600"), null));

        assertThat(old.getAmountPaid()).isEqualByComparingTo("600");
        verify(history, never()).save(any());
    }

    @Test
    void oneTimeSubscriptionExpiresOnceMarked() {
        sub.setBillingCycle(BillingCycle.ONE_TIME);
        sub.setCyclesElapsed(0);
        sub.setStartDate(LocalDate.parse("2026-11-30"));
        period(100L, "2026-11-30", PaymentStatus.PENDING);

        service.mark(1L, 10L, 100L, new MarkPeriodRequest(PaymentStatus.PAID, null, null, null));

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(sub.getNextDueDate()).isEqualTo(LocalDate.parse("2026-11-30"));
        verify(periods, never()).save(any());
    }

    @Test
    void periodOfAnotherSubscriptionIsNotFound() {
        Subscription other = new Subscription();
        other.setId(11L);
        BillingPeriod foreign = new BillingPeriod(other, TODAY, BigDecimal.TEN);
        when(periods.findById(500L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.mark(1L, 10L, 500L,
                new MarkPeriodRequest(PaymentStatus.PAID, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    private BillingPeriod period(Long id, String due, PaymentStatus status) {
        BillingPeriod p = new BillingPeriod(sub, LocalDate.parse(due), sub.getAmount());
        p.setId(id);
        p.setPaymentStatus(status);
        when(periods.findById(id)).thenReturn(Optional.of(p));
        return p;
    }
}
