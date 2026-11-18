package com.subtrack.dto.period;

import com.subtrack.dto.subscription.SubscriptionResponse;

/** The updated period plus the subscription (its due date may have moved forward). */
public record MarkPeriodResponse(BillingPeriodResponse period, SubscriptionResponse subscription) {
}
