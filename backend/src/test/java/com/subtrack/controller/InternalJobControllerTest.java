package com.subtrack.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InternalJobControllerTest {

    @Test
    void secretMustMatchExactly() {
        assertThat(InternalJobController.matches("s3cret", "s3cret")).isTrue();
        assertThat(InternalJobController.matches("s3cre", "s3cret")).isFalse();
        assertThat(InternalJobController.matches(null, "s3cret")).isFalse();
    }

    @Test
    void blankConfiguredSecretNeverMatches() {
        assertThat(InternalJobController.matches("", "")).isFalse();
        assertThat(InternalJobController.matches("x", " ")).isFalse();
    }
}
