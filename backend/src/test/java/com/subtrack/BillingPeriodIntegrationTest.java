package com.subtrack;

import static com.subtrack.support.ApiTestHelper.read;
import static com.subtrack.support.ApiTestHelper.register;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.subtrack.notification.EmailSender;
import com.subtrack.security.RateLimitFilter;
import com.subtrack.service.RenewalService;
import com.subtrack.support.AbstractIntegrationTest;
import com.subtrack.support.ApiTestHelper.TestUser;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Paid / Missed tracking end to end (project plan §5.4, §7.4, §7.5). */
@AutoConfigureMockMvc
class BillingPeriodIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private RenewalService renewalService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmailSender emailSender;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @BeforeEach
    void resetLimits() {
        rateLimitFilter.reset();
    }

    @Test
    void markingCurrentPeriodPaidAdvancesDueDateInOneStep() throws Exception {
        TestUser user = register(mvc);
        LocalDate due = today.plusDays(5);
        Integer subId = create(user, "Netflix", "MONTHLY", due, false);

        var periods = mvc.perform(user.auth(get("/api/subscriptions/" + subId + "/periods")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].displayStatus").value("DUE_SOON"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andReturn();
        Integer periodId = read(periods, "$[0].id");

        mvc.perform(user.json(patch("/api/subscriptions/" + subId + "/periods/" + periodId),
                        "{\"status\":\"PAID\",\"amountPaid\":649}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.period.paidOn").value(today.toString()))
                .andExpect(jsonPath("$.subscription.nextDueDate").value(due.plusMonths(1).toString()));

        mvc.perform(user.auth(get("/api/subscriptions/" + subId + "/periods")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].paymentStatus").value("PAID"));

        // Can't mark a not-yet-due period as missed
        mvc.perform(user.json(patch("/api/subscriptions/" + subId + "/periods/" + periodId),
                        "{\"status\":\"MISSED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overduePeriodMarkedMissedThenCorrectedToPaidIsAudited() throws Exception {
        TestUser user = register(mvc);
        Integer subId = create(user, "Star Health", "YEARLY", today.minusDays(3), false);
        var periods = mvc.perform(user.auth(get("/api/subscriptions/" + subId + "/periods")))
                .andExpect(jsonPath("$[0].displayStatus").value("OVERDUE"))
                .andReturn();
        Integer periodId = read(periods, "$[0].id");

        mvc.perform(user.json(patch("/api/subscriptions/" + subId + "/periods/" + periodId),
                        "{\"status\":\"MISSED\",\"note\":\"Policy lapsed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.nextDueDate").value(today.minusDays(3).plusYears(1).toString()));

        mvc.perform(user.json(patch("/api/subscriptions/" + subId + "/periods/" + periodId),
                        "{\"status\":\"PAID\",\"paidOn\":\"" + today.minusDays(1) + "\"}"))
                .andExpect(status().isOk())
                // A correction never moves the due date again
                .andExpect(jsonPath("$.subscription.nextDueDate").value(today.minusDays(3).plusYears(1).toString()));

        mvc.perform(user.auth(get("/api/periods/" + periodId + "/history")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].oldStatus").value("MISSED"))
                .andExpect(jsonPath("$[0].newStatus").value("PAID"));
    }

    @Test
    void oneTimeSubscriptionExpiresWhenMarked() throws Exception {
        TestUser user = register(mvc);
        Integer subId = create(user, "Course", "ONE_TIME", today.plusDays(2), false);
        Integer periodId = read(mvc.perform(user.auth(get("/api/subscriptions/" + subId + "/periods"))).andReturn(),
                "$[0].id");

        mvc.perform(user.json(patch("/api/subscriptions/" + subId + "/periods/" + periodId), "{\"status\":\"PAID\"}"))
                .andExpect(jsonPath("$.subscription.status").value("EXPIRED"));
    }

    @Test
    void renewalJobAdvancesAutoRenewOnceAndIsIdempotent() throws Exception {
        TestUser user = register(mvc);
        Integer autoId = create(user, "Spotify", "MONTHLY", today.minusDays(40), true);
        Integer manualId = create(user, "Airtel", "MONTHLY", today.minusDays(40), false);

        renewalService.renewOverdueAutoRenewals();
        int afterFirst = countPeriods(autoId);
        renewalService.renewOverdueAutoRenewals();

        assertThat(countPeriods(autoId)).isEqualTo(afterFirst).isGreaterThanOrEqualTo(2);
        mvc.perform(user.auth(get("/api/subscriptions/" + autoId)))
                .andExpect(jsonPath("$.daysLeft").value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)));
        mvc.perform(user.auth(get("/api/subscriptions/" + autoId + "/periods")))
                .andExpect(jsonPath("$[1].displayStatus").value("AWAITING_CONFIRMATION"));

        // Non-auto-renew waits for the user
        assertThat(countPeriods(manualId)).isEqualTo(1);
        mvc.perform(user.auth(get("/api/subscriptions/" + manualId)))
                .andExpect(jsonPath("$.nextDueDate").value(today.minusDays(40).toString()));
    }

    @Test
    void otherUsersCannotMarkOrReadPeriods() throws Exception {
        TestUser owner = register(mvc);
        TestUser other = register(mvc);
        Integer subId = create(owner, "Netflix", "MONTHLY", today.plusDays(5), false);
        Integer periodId = read(mvc.perform(owner.auth(get("/api/subscriptions/" + subId + "/periods"))).andReturn(),
                "$[0].id");

        mvc.perform(other.auth(get("/api/subscriptions/" + subId + "/periods"))).andExpect(status().isNotFound());
        mvc.perform(other.json(patch("/api/subscriptions/" + subId + "/periods/" + periodId), "{\"status\":\"PAID\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(other.auth(get("/api/periods/" + periodId + "/history"))).andExpect(status().isNotFound());
    }

    private Integer create(TestUser user, String name, String cycle, LocalDate due, boolean autoRenew) throws Exception {
        String body = """
                {"name":"%s","category":"OTHER","amount":649,"billingCycle":"%s","nextDueDate":"%s","autoRenew":%s}
                """.formatted(name, cycle, due, autoRenew);
        return read(mvc.perform(user.json(post("/api/subscriptions"), body)).andExpect(status().isCreated()).andReturn(),
                "$.id");
    }

    private int countPeriods(Integer subId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM billing_periods WHERE subscription_id = ?",
                Integer.class, subId);
    }
}
