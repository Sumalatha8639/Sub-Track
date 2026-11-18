package com.subtrack.dto.subscription;

import com.subtrack.enums.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(@NotNull SubscriptionStatus status) {
}
