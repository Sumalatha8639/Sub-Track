package com.subtrack.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.subtrack.support.TestProps;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EmailTemplatesTest {

    private final EmailTemplates templates = new EmailTemplates(TestProps.create());

    @Test
    void verificationEmailLinksToFrontendWithToken() {
        EmailMessage msg = templates.verifyEmail("asha@example.com", "Asha", "tok_123-abc");

        assertThat(msg.to()).isEqualTo("asha@example.com");
        assertThat(msg.subject()).contains("Verify");
        assertThat(msg.text()).contains("http://localhost:5173/verify-email?token=tok_123-abc");
        assertThat(msg.html()).contains("http://localhost:5173/verify-email?token=tok_123-abc");
        assertThat(msg.text()).contains("24 hours");
    }

    @Test
    void resetEmailMentionsExpiry() {
        EmailMessage msg = templates.resetPassword("asha@example.com", "Asha", "tok");

        assertThat(msg.text()).contains("/reset-password?token=tok").contains("30 minutes");
    }

    @Test
    void reminderShowsRupeesDateAndLink() {
        EmailMessage msg = templates.reminder("a@example.com", "Asha", "Star Health",
                new BigDecimal("123456.5"), LocalDate.parse("2026-12-05"), 7, 42L);

        assertThat(msg.subject()).isEqualTo("Star Health renews in 7 days (₹1,23,456.50)");
        assertThat(msg.html()).contains("05 Dec 2026").contains("₹1,23,456.50")
                .contains("http://localhost:5173/subscriptions/42");
        assertThat(templates.reminder("a@example.com", "A", "Jio", BigDecimal.TEN,
                LocalDate.parse("2026-12-05"), 1, 1L).subject()).contains("tomorrow");
    }

    @Test
    void overdueAsksPaidOrMissed() {
        EmailMessage msg = templates.overdue("a@example.com", "Asha", "Airtel", new BigDecimal("499"),
                LocalDate.parse("2026-11-15"), 9L);

        assertThat(msg.subject()).contains("Did you pay Airtel").contains("15 Nov 2026");
        assertThat(msg.text()).contains("/subscriptions/9");
    }

    @Test
    void userNameIsHtmlEscaped() {
        EmailMessage msg = templates.verifyEmail("x@example.com", "<script>alert(1)</script>", "tok");

        assertThat(msg.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
