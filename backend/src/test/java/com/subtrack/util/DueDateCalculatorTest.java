package com.subtrack.util;

import static com.subtrack.enums.BillingCycle.HALF_YEARLY;
import static com.subtrack.enums.BillingCycle.MONTHLY;
import static com.subtrack.enums.BillingCycle.ONE_TIME;
import static com.subtrack.enums.BillingCycle.QUARTERLY;
import static com.subtrack.enums.BillingCycle.YEARLY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.subtrack.enums.BillingCycle;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DueDateCalculatorTest {

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    @Test
    void cycleZeroIsTheAnchor() {
        assertThat(DueDateCalculator.dueDate(d("2026-12-05"), MONTHLY, 0)).isEqualTo(d("2026-12-05"));
        assertThat(DueDateCalculator.dueDate(d("2026-12-05"), ONE_TIME, 0)).isEqualTo(d("2026-12-05"));
    }

    @Test
    void monthEndAnchorDoesNotDrift() {
        LocalDate anchor = d("2027-01-31");
        assertThat(DueDateCalculator.dueDate(anchor, MONTHLY, 1)).isEqualTo(d("2027-02-28"));
        assertThat(DueDateCalculator.dueDate(anchor, MONTHLY, 2)).isEqualTo(d("2027-03-31"));
        assertThat(DueDateCalculator.dueDate(anchor, MONTHLY, 3)).isEqualTo(d("2027-04-30"));
        assertThat(DueDateCalculator.dueDate(anchor, MONTHLY, 4)).isEqualTo(d("2027-05-31"));
    }

    @Test
    void leapDayYearlyFallsBackToFeb28AndReturnsOnLeapYears() {
        LocalDate anchor = d("2028-02-29");
        assertThat(DueDateCalculator.dueDate(anchor, YEARLY, 1)).isEqualTo(d("2029-02-28"));
        assertThat(DueDateCalculator.dueDate(anchor, YEARLY, 4)).isEqualTo(d("2032-02-29"));
    }

    @ParameterizedTest
    @CsvSource({
            "MONTHLY,     3, 2027-04-15",
            "QUARTERLY,   1, 2027-04-15",
            "QUARTERLY,   4, 2028-01-15",
            "HALF_YEARLY, 1, 2027-07-15",
            "YEARLY,      2, 2029-01-15",
    })
    void allCycles(BillingCycle cycle, int n, LocalDate expected) {
        assertThat(DueDateCalculator.dueDate(d("2027-01-15"), cycle, n)).isEqualTo(expected);
    }

    @Test
    void oneTimeHasNoNextCycle() {
        assertThatThrownBy(() -> DueDateCalculator.dueDate(d("2027-01-15"), ONE_TIME, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cyclesUntilSkipsSeveralMissedCycles() {
        LocalDate anchor = d("2026-01-31");
        // Due dates: Jan 31, Feb 28, Mar 31, Apr 30, May 31 ...
        assertThat(DueDateCalculator.cyclesUntilOnOrAfter(anchor, MONTHLY, d("2026-01-01"))).isZero();
        assertThat(DueDateCalculator.cyclesUntilOnOrAfter(anchor, MONTHLY, d("2026-01-31"))).isZero();
        assertThat(DueDateCalculator.cyclesUntilOnOrAfter(anchor, MONTHLY, d("2026-02-01"))).isEqualTo(1);
        assertThat(DueDateCalculator.cyclesUntilOnOrAfter(anchor, MONTHLY, d("2026-02-28"))).isEqualTo(1);
        assertThat(DueDateCalculator.cyclesUntilOnOrAfter(anchor, MONTHLY, d("2026-03-01"))).isEqualTo(2);
        assertThat(DueDateCalculator.cyclesUntilOnOrAfter(anchor, MONTHLY, d("2026-05-01"))).isEqualTo(4);
        assertThat(DueDateCalculator.cyclesUntilOnOrAfter(anchor, QUARTERLY, d("2026-11-16"))).isEqualTo(4);
        assertThat(DueDateCalculator.cyclesUntilOnOrAfter(anchor, HALF_YEARLY, d("2026-08-01"))).isEqualTo(2);
    }
}
