package com.subtrack.dto.subscription;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Replaces all reminder days. An empty list turns reminders off for this subscription. */
public record ReminderDaysRequest(@NotNull @Size(max = 10) List<@NotNull @Min(1) @Max(90) Integer> days) {
}
