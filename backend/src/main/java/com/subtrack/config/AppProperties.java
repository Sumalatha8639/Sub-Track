package com.subtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code app.*} settings in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String frontendUrl,
        String defaultTimezone,
        Jwt jwt,
        Jobs jobs,
        Mail mail,
        Auth auth,
        Reminders reminders) {

    public record Jwt(String secret, int accessTokenMinutes, int refreshTokenDays) {
    }

    public record Jobs(String secret) {
    }

    /** @param from sender shown on every email, e.g. "SubTrack &lt;no-reply@subtrack.local&gt;" */
    public record Mail(String from) {
    }

    public record Auth(
            int verificationTokenHours,
            int resetTokenMinutes,
            boolean refreshCookieSecure,
            String refreshCookieSameSite) {
    }

    /**
     * @param sendHour local hour (0–23) at which reminders go out; -1 sends at any hour (tests)
     * @param overdueNudgeMaxDays the overdue nudge is only sent for periods at most this many days late
     */
    public record Reminders(int sendHour, int overdueNudgeMaxDays) {
    }
}
