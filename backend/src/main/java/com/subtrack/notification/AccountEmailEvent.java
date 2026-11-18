package com.subtrack.notification;

/**
 * Published inside a transaction; the email is sent only after the transaction commits,
 * so a rollback never sends a link for a token that doesn't exist.
 */
public record AccountEmailEvent(Kind kind, String to, String name, String rawToken) {

    public enum Kind {
        VERIFY_EMAIL,
        RESET_PASSWORD
    }
}
