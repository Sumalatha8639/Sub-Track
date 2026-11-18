package com.subtrack.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PeriodDisplayStatusTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-16");

    @ParameterizedTest(name = "{0}, due in {1} days, autoRenew={2} -> {3}")
    @CsvSource({
            "PAID,     -30, false, PAID",
            "PAID,      10, false, PAID",
            "MISSED,    -2, true,  MISSED",
            "PENDING,   30, false, UPCOMING",
            "PENDING,    8, false, UPCOMING",
            "PENDING,    7, false, DUE_SOON",
            "PENDING,    1, true,  DUE_SOON",
            "PENDING,    0, false, DUE_TODAY",
            "PENDING,    0, true,  DUE_TODAY",
            "PENDING,   -1, false, OVERDUE",
            "PENDING,  -40, false, OVERDUE",
            "PENDING,   -1, true,  AWAITING_CONFIRMATION",
    })
    void displayStatus(PaymentStatus status, int dueInDays, boolean autoRenew, PeriodDisplayStatus expected) {
        assertThat(PeriodDisplayStatus.of(status, TODAY.plusDays(dueInDays), autoRenew, TODAY)).isEqualTo(expected);
    }
}
