package com.subtrack.support;

import com.subtrack.config.AppProperties;

/** AppProperties for unit tests, matching the defaults in application.yml. */
public final class TestProps {

    public static final String FRONTEND = "http://localhost:5173";
    public static final String JWT_SECRET = "unit-test-secret-unit-test-secret-unit-test-secret-0123456789";

    private TestProps() {
    }

    public static AppProperties create() {
        return withSendHour(9);
    }

    public static AppProperties withSendHour(int sendHour) {
        return new AppProperties(
                FRONTEND,
                "Asia/Kolkata",
                new AppProperties.Jwt(JWT_SECRET, 15, 7),
                new AppProperties.Jobs("job-secret"),
                new AppProperties.Mail("SubTrack <no-reply@subtrack.local>"),
                new AppProperties.Auth(24, 30, false, "Lax"),
                new AppProperties.Reminders(sendHour, 30));
    }
}
