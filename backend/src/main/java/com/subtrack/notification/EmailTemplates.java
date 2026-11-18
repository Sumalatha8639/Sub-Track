package com.subtrack.notification;

import com.subtrack.config.AppProperties;
import com.subtrack.util.InrFormat;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Builds the transactional emails. User-supplied values are HTML-escaped. */
@Component
@RequiredArgsConstructor
public class EmailTemplates {

    private final AppProperties props;

    public EmailMessage verifyEmail(String to, String name, String rawToken) {
        String link = props.frontendUrl() + "/verify-email?token=" + encode(rawToken);
        int hours = props.auth().verificationTokenHours();
        return new EmailMessage(to, "Verify your SubTrack email",
                layout("Verify your email", """
                        <p>Hi %s,</p>
                        <p>Thanks for signing up for SubTrack. Please confirm your email address so we can send you renewal reminders.</p>
                        %s
                        <p style="color:#64748b;font-size:13px">This link expires in %d hours. If you didn't sign up, you can ignore this email.</p>
                        """.formatted(esc(name), button(link, "Verify email"), hours)),
                """
                Hi %s,

                Confirm your email for SubTrack by opening this link:
                %s

                This link expires in %d hours. If you didn't sign up, ignore this email.
                """.formatted(name, link, hours));
    }

    public EmailMessage resetPassword(String to, String name, String rawToken) {
        String link = props.frontendUrl() + "/reset-password?token=" + encode(rawToken);
        int minutes = props.auth().resetTokenMinutes();
        return new EmailMessage(to, "Reset your SubTrack password",
                layout("Reset your password", """
                        <p>Hi %s,</p>
                        <p>We received a request to reset your SubTrack password.</p>
                        %s
                        <p style="color:#64748b;font-size:13px">This link expires in %d minutes and can be used once. If you didn't ask for this, you can ignore this email; your password won't change.</p>
                        """.formatted(esc(name), button(link, "Reset password"), minutes)),
                """
                Hi %s,

                Reset your SubTrack password with this link:
                %s

                It expires in %d minutes and can be used once. If you didn't ask for this, ignore this email.
                """.formatted(name, link, minutes));
    }

    /** Renewal reminder (project plan §3.1). */
    public EmailMessage reminder(String to, String name, String subscription, BigDecimal amount,
                                 LocalDate dueDate, long daysLeft, Long subscriptionId) {
        String when = daysLeft <= 0 ? "today" : daysLeft == 1 ? "tomorrow" : "in " + daysLeft + " days";
        String link = props.frontendUrl() + "/subscriptions/" + subscriptionId;
        String subject = subscription + " renews " + when + " (" + inr(amount) + ")";
        return new EmailMessage(to, subject,
                layout(subscription + " is due " + when, """
                        <p>Hi %s,</p>
                        <p>This is a reminder that <b>%s</b> is due on <b>%s</b>.</p>
                        <table role="presentation" style="margin:16px 0;border-collapse:collapse">
                        <tr><td style="padding:4px 16px 4px 0;color:#64748b">Amount</td><td style="padding:4px 0;font-weight:600">%s</td></tr>
                        <tr><td style="padding:4px 16px 4px 0;color:#64748b">Due date</td><td style="padding:4px 0;font-weight:600">%s</td></tr>
                        </table>
                        <p>Once it's paid, mark it as paid so we stop reminding you.</p>
                        %s
                        """.formatted(esc(name), esc(subscription), date(dueDate), inr(amount), date(dueDate),
                        button(link, "Open in SubTrack"))),
                """
                Hi %s,

                %s is due on %s (%s).
                Amount: %s

                Once it's paid, mark it as paid in SubTrack: %s
                """.formatted(name, subscription, date(dueDate), when, inr(amount), link));
    }

    /** One nudge the day after a manual-renewal period went unmarked (project plan §3.1). */
    public EmailMessage overdue(String to, String name, String subscription, BigDecimal amount,
                                LocalDate dueDate, Long subscriptionId) {
        String link = props.frontendUrl() + "/subscriptions/" + subscriptionId;
        return new EmailMessage(to, "Did you pay " + subscription + "? It was due " + date(dueDate),
                layout("Did you pay " + subscription + "?", """
                        <p>Hi %s,</p>
                        <p><b>%s</b> (%s) was due on <b>%s</b> and hasn't been marked yet.</p>
                        <p>Let us know whether you paid it or missed it, so your records stay accurate.</p>
                        %s
                        """.formatted(esc(name), esc(subscription), inr(amount), date(dueDate),
                        button(link, "Mark as paid or missed"))),
                """
                Hi %s,

                %s (%s) was due on %s and hasn't been marked yet.
                Mark it as paid or missed: %s
                """.formatted(name, subscription, inr(amount), date(dueDate), link));
    }

    // ---- shared pieces -------------------------------------------------

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    static String date(LocalDate date) {
        return DATE.format(date);
    }

    /** ₹1,23,456.50 (Indian digit grouping). */
    static String inr(BigDecimal amount) {
        return InrFormat.format(amount);
    }

    static String layout(String heading, String body) {
        return """
                <!doctype html>
                <html><body style="margin:0;background:#f8fafc;font-family:Segoe UI,Roboto,Arial,sans-serif;color:#0f172a">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px 12px">
                <table role="presentation" width="100%%" style="max-width:520px;background:#ffffff;border:1px solid #e2e8f0;border-radius:12px">
                <tr><td style="padding:24px">
                <p style="margin:0 0 4px;color:#4f46e5;font-weight:600">SubTrack</p>
                <h1 style="margin:0 0 16px;font-size:20px">%s</h1>
                %s
                </td></tr></table>
                </td></tr></table>
                </body></html>
                """.formatted(esc(heading), body);
    }

    static String button(String href, String label) {
        return """
                <p style="margin:24px 0"><a href="%s" style="background:#4f46e5;color:#ffffff;text-decoration:none;padding:10px 18px;border-radius:8px;display:inline-block;font-weight:600">%s</a></p>
                """.formatted(esc(href), esc(label));
    }

    static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
