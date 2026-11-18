package com.subtrack.dto.period;

import com.subtrack.entity.BillingPeriod;
import com.subtrack.enums.PaymentStatus;
import com.subtrack.enums.PeriodDisplayStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record BillingPeriodResponse(
        Long id,
        Long subscriptionId,
        LocalDate dueDate,
        BigDecimal amountDue,
        PaymentStatus paymentStatus,
        PeriodDisplayStatus displayStatus,
        /** Days from today to the due date; negative when past due. */
        long daysLeft,
        LocalDate paidOn,
        BigDecimal amountPaid,
        String note,
        Instant markedAt,
        /** True for the period of the subscription's next due date. */
        boolean current) {

    public static BillingPeriodResponse from(BillingPeriod p, LocalDate today) {
        return new BillingPeriodResponse(
                p.getId(), p.getSubscription().getId(), p.getDueDate(), p.getAmountDue(),
                p.getPaymentStatus(), PeriodDisplayStatus.of(p, today),
                ChronoUnit.DAYS.between(today, p.getDueDate()),
                p.getPaidOn(), p.getAmountPaid(), p.getNote(), p.getMarkedAt(),
                p.getDueDate().equals(p.getSubscription().getNextDueDate()));
    }
}
