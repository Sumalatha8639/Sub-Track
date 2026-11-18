package com.subtrack.dto.period;

import com.subtrack.entity.BillingPeriodHistory;
import com.subtrack.enums.PaymentStatus;
import java.time.Instant;

public record PeriodHistoryResponse(Long id, PaymentStatus oldStatus, PaymentStatus newStatus, Instant changedAt) {

    public static PeriodHistoryResponse from(BillingPeriodHistory h) {
        return new PeriodHistoryResponse(h.getId(), h.getOldStatus(), h.getNewStatus(), h.getChangedAt());
    }
}
