package com.subtrack.dto.subscription;

import com.subtrack.enums.BillingCycle;
import com.subtrack.enums.Category;
import com.subtrack.enums.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SubscriptionResponse(
        Long id,
        String name,
        Category category,
        BigDecimal amount,
        BigDecimal monthlyAmount,
        BillingCycle billingCycle,
        LocalDate anchorDate,
        LocalDate nextDueDate,
        /** Days from today (user's timezone) to nextDueDate; negative when past due. */
        long daysLeft,
        SubscriptionStatus status,
        boolean autoRenew,
        String notes,
        List<Integer> reminderDays,
        Instant createdAt,
        Instant updatedAt) {
}
