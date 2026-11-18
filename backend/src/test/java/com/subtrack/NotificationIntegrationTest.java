package com.subtrack;

import static com.subtrack.support.ApiTestHelper.read;
import static com.subtrack.support.ApiTestHelper.register;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.subtrack.notification.EmailMessage;
import com.subtrack.notification.EmailSendException;
import com.subtrack.notification.EmailSender;
import com.subtrack.notification.NotificationService;
import com.subtrack.security.RateLimitFilter;
import com.subtrack.support.AbstractIntegrationTest;
import com.subtrack.support.ApiTestHelper.TestUser;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Reminder delivery: exactly once, retries, cron endpoint (project plan §7.2, §7.3). */
@AutoConfigureMockMvc
class NotificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmailSender emailSender;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @BeforeEach
    void setUp() {
        rateLimitFilter.reset();
        reset(emailSender);
    }

    @Test
    void twoConcurrentRunsSendEachReminderExactlyOnce() throws Exception {
        TestUser user = verifiedUser();
        createSubscription(user, "Netflix", today.plusDays(7), false);
        createSubscription(user, "Airtel", today.minusDays(1), false); // overdue nudge

        CountDownLatch start = new CountDownLatch(1);
        Callable<NotificationService.RunResult> run = () -> {
            start.await();
            return notificationService.sendDueReminders();
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<NotificationService.RunResult> a = pool.submit(run);
            Future<NotificationService.RunResult> b = pool.submit(run);
            start.countDown();
            a.get();
            b.get();
        } finally {
            pool.shutdown();
        }

        List<EmailMessage> mine = sentTo(user.email());
        assertThat(mine).hasSize(2);
        assertThat(mine).extracting(EmailMessage::subject)
                .anySatisfy(s -> assertThat(s).contains("Netflix").contains("in 7 days"))
                .anySatisfy(s -> assertThat(s).contains("Did you pay Airtel"));

        // A third run sends nothing new
        notificationService.sendDueReminders();
        assertThat(sentTo(user.email())).hasSize(2);
    }

    @Test
    void unverifiedUsersGetNoReminders() throws Exception {
        TestUser user = register(mvc);
        createSubscription(user, "Netflix", today.plusDays(3), false);

        notificationService.sendDueReminders();

        assertThat(sentTo(user.email())).isEmpty();
    }

    @Test
    void failedEmailIsRetriedAndThenSent() throws Exception {
        TestUser user = verifiedUser();
        createSubscription(user, "Hotstar", today.plusDays(3), false);

        doThrow(new EmailSendException("smtp down", null)).when(emailSender).send(any());
        notificationService.sendDueReminders();
        Long id = jdbc.queryForObject("""
                SELECT n.id FROM notification_log n
                JOIN billing_periods p ON p.id = n.billing_period_id
                JOIN subscriptions s ON s.id = p.subscription_id
                JOIN users u ON u.id = s.user_id
                WHERE u.email = ? AND n.status = 'FAILED' AND n.attempt_count = 1
                  AND n.next_retry_at IS NOT NULL
                """, Long.class, user.email());

        // Pretend the 15 minute backoff has passed
        jdbc.update("UPDATE notification_log SET next_retry_at = NOW(6) - INTERVAL 1 MINUTE WHERE id = ?", id);
        doNothing().when(emailSender).send(any());
        notificationService.retryFailed();

        assertThat(jdbc.queryForObject("SELECT status FROM notification_log WHERE id = ?", String.class, id))
                .isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM notification_log WHERE id = ?", Integer.class, id))
                .isEqualTo(2);

        mvc.perform(user.auth(get("/api/notifications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].subscriptionName").value("Hotstar"))
                .andExpect(jsonPath("$.content[0].status").value("SENT"))
                .andExpect(jsonPath("$.content[0].daysBefore").value(3));
    }

    @Test
    void cronEndpointNeedsTheSecret() throws Exception {
        mvc.perform(post("/internal/jobs/run")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/jobs/run").header("X-Job-Secret", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/jobs/run").header("X-Job-Secret", "local-job-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ran").value(true));
        mvc.perform(get("/internal/health")).andExpect(status().isOk());
    }

    // ---- helpers -------------------------------------------------------------

    private TestUser verifiedUser() throws Exception {
        TestUser user = register(mvc);
        jdbc.update("UPDATE users SET email_verified = TRUE WHERE email = ?", user.email());
        return user;
    }

    private void createSubscription(TestUser user, String name, LocalDate due, boolean autoRenew) throws Exception {
        String body = """
                {"name":"%s","category":"OTT","amount":649,"billingCycle":"MONTHLY","nextDueDate":"%s","autoRenew":%s}
                """.formatted(name, due, autoRenew);
        read(mvc.perform(user.json(post("/api/subscriptions"), body)).andExpect(status().isCreated()).andReturn(), "$.id");
    }

    /** Reminder/overdue emails sent to this address (the verification email from sign-up is ignored). */
    private List<EmailMessage> sentTo(String email) {
        return Mockito.mockingDetails(emailSender).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("send"))
                .map(i -> (EmailMessage) i.getArgument(0))
                .filter(m -> m.to().equals(email))
                .filter(m -> !m.subject().startsWith("Verify"))
                .toList();
    }
}
