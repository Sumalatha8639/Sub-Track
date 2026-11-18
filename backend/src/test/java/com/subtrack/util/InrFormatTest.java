package com.subtrack.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InrFormatTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0          | ₹0.00",
            "5          | ₹5.00",
            "649        | ₹649.00",
            "1000       | ₹1,000.00",
            "18500      | ₹18,500.00",
            "123456.5   | ₹1,23,456.50",
            "1234567    | ₹12,34,567.00",
            "10000000   | ₹1,00,00,000.00",
            "99.999     | ₹100.00",
            "-2500      | -₹2,500.00",
    })
    void indianGrouping(String amount, String expected) {
        assertThat(InrFormat.format(new BigDecimal(amount))).isEqualTo(expected);
    }
}
