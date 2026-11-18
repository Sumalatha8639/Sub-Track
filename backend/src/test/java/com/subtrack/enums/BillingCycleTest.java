package com.subtrack.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BillingCycleTest {

    @Test
    void cycleLengthsMatchProjectPlan() {
        assertThat(BillingCycle.MONTHLY.getMonths()).isEqualTo(1);
        assertThat(BillingCycle.QUARTERLY.getMonths()).isEqualTo(3);
        assertThat(BillingCycle.HALF_YEARLY.getMonths()).isEqualTo(6);
        assertThat(BillingCycle.YEARLY.getMonths()).isEqualTo(12);
    }

    @Test
    void onlyOneTimeIsNotRecurring() {
        for (BillingCycle cycle : BillingCycle.values()) {
            assertThat(cycle.isRecurring()).isEqualTo(cycle != BillingCycle.ONE_TIME);
        }
    }
}
