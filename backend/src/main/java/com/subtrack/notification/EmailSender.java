package com.subtrack.notification;

/**
 * Sends one email (implemented by {@link SmtpEmailSender}; tests replace it with a mock).
 * Throws {@link EmailSendException} on failure so callers can log and retry.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
