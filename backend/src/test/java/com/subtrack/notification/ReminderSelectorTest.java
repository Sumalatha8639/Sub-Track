package com.subtrack.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.subtrack.enums.NotificationType;
import com.subtrack.notification.ReminderSelector.Alert;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReminderSelectorTest {

    private static final List<Integer> DEFAULTS = List.of(30, 15, 7, 3, 1);

    /** The table from project plan §7.2. */
    @ParameterizedTest(name = "{0} days left -> reminder {1}")
    @CsvSource({
            "30, 30",
            "28, 30",   // 30-day run was missed: still send it
            "16, 30",
            "15, 15",
            "12, 15",
            "7,  7",
            "5,  7",
            "3,  3",
            "2,  3",
            "1,  1",
    })
    void catchUpRule(long daysLeft, int expectedDay) {
        assertThat(ReminderSelector.select(daysLeft, DEFAULTS, false, 30))
                .contains(new Alert(NotificationType.REMINDER, expectedDay));
    }

    @Test
    void nothingBeyondTheLargestReminder() {
        assertThat(ReminderSelector.select(31, DEFAULTS, false, 30)).isEmpty();
        assertThat(ReminderSelector.select(8, List.of(7, 3, 1), false, 30)).isEmpty();
    }

    @Test
    void customIntervals() {
        assertThat(ReminderSelector.select(50, List.of(60, 10), false, 30))
                .contains(new Alert(NotificationType.REMINDER, 60));
        assertThat(ReminderSelector.select(10, List.of(60, 10), false, 30))
                .contains(new Alert(NotificationType.REMINDER, 10));
        assertThat(ReminderSelector.select(5, List.of(60, 10), false, 30))
                .contains(new Alert(NotificationType.REMINDER, 10));
    }

    @Test
    void noRemindersConfiguredMeansNoReminder() {
        assertThat(ReminderSelector.select(3, List.of(), false, 30)).isEmpty();
    }

    @Test
    void dueTodayGetsNothing() {
        assertThat(ReminderSelector.select(0, DEFAULTS, false, 30)).isEmpty();
    }

    @Test
    void overdueNudgeOnlyForManualRenewalsAndRecentPeriods() {
        Optional<Alert> nudge = ReminderSelector.select(-1, DEFAULTS, false, 30);
        assertThat(nudge).contains(new Alert(NotificationType.OVERDUE, ReminderSelector.OVERDUE_DAYS_BEFORE));
        assertThat(ReminderSelector.select(-30, DEFAULTS, false, 30)).isPresent();
        assertThat(ReminderSelector.select(-31, DEFAULTS, false, 30)).isEmpty();
        assertThat(ReminderSelector.select(-1, DEFAULTS, true, 30)).isEmpty();
    }

    @Test
    void lookAheadIsLargestReminder() {
        assertThat(ReminderSelector.lookAheadDays(List.of(7, 60, 3))).isEqualTo(60);
        assertThat(ReminderSelector.lookAheadDays(List.of())).isZero();
    }
}
