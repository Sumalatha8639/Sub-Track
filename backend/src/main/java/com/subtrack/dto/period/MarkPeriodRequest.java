package com.subtrack.dto.period;

import com.subtrack.enums.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Mark a period PAID or MISSED (or correct it). For PAID, paidOn defaults to today and
 * amountPaid to the amount due. paidOn/amountPaid are ignored for MISSED.
 */
public record MarkPeriodRequest(
        @NotNull PaymentStatus status,
        LocalDate paidOn,
        @DecimalMin(value = "0.01", message = "must be greater than 0") @Digits(integer = 10, fraction = 2)
        BigDecimal amountPaid,
        @Size(max = 500) String note) {
}
