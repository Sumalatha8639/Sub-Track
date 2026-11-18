package com.subtrack.dto.subscription;

import com.subtrack.enums.BillingCycle;
import com.subtrack.enums.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Create or update a subscription. Amount is in INR. */
public record SubscriptionRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull Category category,
        @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0")
        @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull BillingCycle billingCycle,
        /** The next date this bill is due; becomes the anchor for later due dates. */
        @NotNull LocalDate nextDueDate,
        /** Optional; defaults to false. A wrapper so leaving it out isn't a JSON error. */
        Boolean autoRenew,
        @Size(max = 500) String notes,
        /** Optional on create (defaults by cycle); ignored on update (use PUT /reminders). */
        @Size(max = 10) List<@NotNull @Min(1) @Max(90) Integer> reminderDays) {

    public boolean autoRenewEnabled() {
        return Boolean.TRUE.equals(autoRenew);
    }
}
