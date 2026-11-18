package com.subtrack.enums;

/**
 * Stored payment status of a billing period. Display labels such as Overdue or
 * Awaiting confirmation are computed from PENDING + due date + auto-renew.
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    MISSED
}
