package com.subtrack.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends verification and password-reset emails after commit. A failure is logged, not thrown:
 * the user can ask for the email again (resend verification / forgot password).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEmailListener {

    private final EmailSender emailSender;
    private final EmailTemplates templates;

    @TransactionalEventListener
    public void onAccountEmail(AccountEmailEvent event) {
        EmailMessage message = switch (event.kind()) {
            case VERIFY_EMAIL -> templates.verifyEmail(event.to(), event.name(), event.rawToken());
            case RESET_PASSWORD -> templates.resetPassword(event.to(), event.name(), event.rawToken());
        };
        try {
            emailSender.send(message);
        } catch (RuntimeException e) {
            log.warn("{} email to {} failed: {}", event.kind(), event.to(), e.getMessage());
        }
    }
}
