package com.subtrack.enums;

public enum NotificationStatus {
    /** Claimed by a job run, not yet sent. */
    PENDING,
    SENT,
    FAILED,
    /** Gave up: too many attempts, or the alert is no longer useful. */
    ABANDONED
}
