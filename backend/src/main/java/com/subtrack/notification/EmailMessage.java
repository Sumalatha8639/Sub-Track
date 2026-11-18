package com.subtrack.notification;

/** A ready-to-send email with HTML and plain-text bodies. */
public record EmailMessage(String to, String subject, String html, String text) {
}
