package com.subtrack.util;

import com.subtrack.exception.BadRequestException;
import java.time.DateTimeException;
import java.time.ZoneId;

public final class Timezones {

    private Timezones() {
    }

    /** Validates an IANA zone id (e.g. Asia/Kolkata); blank falls back to the default. */
    public static String resolve(String requested, String fallback) {
        if (requested == null || requested.isBlank()) {
            return fallback;
        }
        try {
            return ZoneId.of(requested.trim()).getId();
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown timezone: " + requested);
        }
    }
}
