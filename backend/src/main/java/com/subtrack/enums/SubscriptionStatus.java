package com.subtrack.enums;

public enum SubscriptionStatus {
    ACTIVE,
    PAUSED,
    CANCELLED,
    /** A ONE_TIME subscription whose only period has been marked. */
    EXPIRED
}
