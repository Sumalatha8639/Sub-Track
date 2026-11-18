package com.subtrack.dto.notification;

import com.subtrack.entity.NotificationLog;
import com.subtrack.enums.NotificationChannel;
import com.subtrack.enums.NotificationStatus;
import com.subtrack.enums.NotificationType;
import java.time.Instant;
import java.time.LocalDate;

public record NotificationResponse(
        Long id,
        Long subscriptionId,
        String subscriptionName,
        LocalDate dueDate,
        NotificationType type,
        /** Reminder threshold in days; -1 for the overdue nudge. */
        int daysBefore,
        NotificationChannel channel,
        NotificationStatus status,
        int attemptCount,
        Instant nextRetryAt,
        Instant createdAt,
        Instant sentAt) {

    public static NotificationResponse from(NotificationLog n) {
        var period = n.getBillingPeriod();
        var sub = period.getSubscription();
        return new NotificationResponse(n.getId(), sub.getId(), sub.getName(), period.getDueDate(), n.getType(),
                n.getDaysBefore(), n.getChannel(), n.getStatus(), n.getAttemptCount(), n.getNextRetryAt(),
                n.getCreatedAt(), n.getSentAt());
    }
}
