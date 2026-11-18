package com.subtrack.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.subtrack.entity.NotificationLog;
import com.subtrack.enums.NotificationStatus;
import com.subtrack.repository.BillingPeriodRepository;
import com.subtrack.repository.NotificationLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationLogWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-16T03:30:00Z");

    private final NotificationLogRepository logs = mock(NotificationLogRepository.class);
    private final NotificationLogWriter writer = new NotificationLogWriter(logs, mock(BillingPeriodRepository.class));

    @Test
    void backoffIs15m1h4h12hThenAbandon() {
        NotificationLog row = new NotificationLog();
        when(logs.findById(1L)).thenReturn(Optional.of(row));

        Duration[] expected = {Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(4), Duration.ofHours(12)};
        for (Duration delay : expected) {
            assertThat(writer.markFailed(1L, NOW, "smtp down")).isEqualTo(NotificationStatus.FAILED);
            assertThat(row.getNextRetryAt()).isEqualTo(NOW.plus(delay));
        }
        assertThat(writer.markFailed(1L, NOW, "smtp down")).isEqualTo(NotificationStatus.ABANDONED);
        assertThat(row.getAttemptCount()).isEqualTo(NotificationLogWriter.MAX_ATTEMPTS);
        assertThat(row.getNextRetryAt()).isNull();
        assertThat(row.getErrorMessage()).isEqualTo("smtp down");
    }

    @Test
    void sentClearsRetryState() {
        NotificationLog row = new NotificationLog();
        row.setStatus(NotificationStatus.FAILED);
        row.setAttemptCount(2);
        row.setNextRetryAt(NOW);
        row.setErrorMessage("x");
        when(logs.findById(1L)).thenReturn(Optional.of(row));

        writer.markSent(1L, NOW);

        assertThat(row.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(row.getAttemptCount()).isEqualTo(3);
        assertThat(row.getSentAt()).isEqualTo(NOW);
        assertThat(row.getNextRetryAt()).isNull();
        assertThat(row.getErrorMessage()).isNull();
    }

    @Test
    void longErrorsAreTruncated() {
        NotificationLog row = new NotificationLog();
        when(logs.findById(1L)).thenReturn(Optional.of(row));

        writer.markFailed(1L, NOW, "e".repeat(5000));

        assertThat(row.getErrorMessage()).hasSize(1000).endsWith("...");
    }
}
