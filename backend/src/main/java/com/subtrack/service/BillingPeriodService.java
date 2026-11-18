package com.subtrack.service;

import com.subtrack.dto.period.BillingPeriodResponse;
import com.subtrack.dto.period.MarkPeriodRequest;
import com.subtrack.dto.period.MarkPeriodResponse;
import com.subtrack.dto.period.PeriodHistoryResponse;
import com.subtrack.entity.BillingPeriod;
import com.subtrack.entity.BillingPeriodHistory;
import com.subtrack.entity.Subscription;
import com.subtrack.enums.PaymentStatus;
import com.subtrack.enums.SubscriptionStatus;
import com.subtrack.exception.BadRequestException;
import com.subtrack.exception.NotFoundException;
import com.subtrack.repository.BillingPeriodHistoryRepository;
import com.subtrack.repository.BillingPeriodRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Paid / Missed tracking per billing cycle (project plan §5.4, §7.5). */
@Service
@RequiredArgsConstructor
public class BillingPeriodService {

    private final SubscriptionService subscriptionService;
    private final BillingPeriodRepository periods;
    private final BillingPeriodHistoryRepository history;
    private final CycleAdvancer advancer;
    private final UserTime userTime;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<BillingPeriodResponse> list(Long userId, Long subscriptionId) {
        Subscription sub = subscriptionService.load(userId, subscriptionId);
        LocalDate today = userTime.today(sub.getUser());
        return periods.findBySubscriptionIdOrderByDueDateDesc(sub.getId()).stream()
                .map(p -> BillingPeriodResponse.from(p, today))
                .toList();
    }

    /**
     * Marks or corrects a period. Marking the current PENDING period moves the subscription to
     * its next cycle; corrections (PAID ↔ MISSED) never move dates again. One transaction.
     */
    @Transactional
    public MarkPeriodResponse mark(Long userId, Long subscriptionId, Long periodId, MarkPeriodRequest req) {
        Subscription sub = subscriptionService.load(userId, subscriptionId);
        BillingPeriod period = periods.findById(periodId)
                .filter(p -> p.getSubscription().getId().equals(sub.getId()))
                .orElseThrow(() -> new NotFoundException("Billing period not found"));
        LocalDate today = userTime.today(sub.getUser());
        Instant now = clock.instant();

        PaymentStatus target = req.status();
        if (target == PaymentStatus.PENDING) {
            throw new BadRequestException("A period can only be marked PAID or MISSED");
        }
        PaymentStatus previous = period.getPaymentStatus();
        boolean wasCurrentPending = previous == PaymentStatus.PENDING
                && period.getDueDate().equals(sub.getNextDueDate());

        if (target == PaymentStatus.PAID) {
            LocalDate paidOn = req.paidOn() != null ? req.paidOn() : today;
            if (paidOn.isAfter(today)) {
                throw new BadRequestException("Paid-on date can't be in the future");
            }
            BigDecimal amount = req.amountPaid() != null ? req.amountPaid() : period.getAmountDue();
            period.setPaidOn(paidOn);
            period.setAmountPaid(amount);
        } else {
            if (period.getDueDate().isAfter(today)) {
                throw new BadRequestException("A period can't be marked missed before its due date");
            }
            period.setPaidOn(null);
            period.setAmountPaid(null);
        }
        period.setPaymentStatus(target);
        period.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        period.setMarkedAt(now);

        if (previous != target) {
            history.save(new BillingPeriodHistory(period, previous, target, now));
        }
        if (wasCurrentPending && sub.getStatus() != SubscriptionStatus.CANCELLED) {
            advancer.advance(sub);
        }
        periods.flush();
        return new MarkPeriodResponse(BillingPeriodResponse.from(period, today),
                SubscriptionService.toResponse(sub, today));
    }

    @Transactional(readOnly = true)
    public List<PeriodHistoryResponse> history(Long userId, Long periodId) {
        BillingPeriod period = periods.findById(periodId)
                .filter(p -> p.getSubscription().getUser().getId().equals(userId))
                .filter(p -> !p.getSubscription().isDeleted())
                .orElseThrow(() -> new NotFoundException("Billing period not found"));
        return history.findByBillingPeriodIdOrderByChangedAtDesc(period.getId()).stream()
                .map(PeriodHistoryResponse::from)
                .toList();
    }
}
