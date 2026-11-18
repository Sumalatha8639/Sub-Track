package com.subtrack.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every class that needs "now" or "today" injects this Clock instead of calling
 * LocalDate.now() directly, so tests can pin the date.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
