package com.subtrack;

import static com.subtrack.support.ApiTestHelper.read;
import static com.subtrack.support.ApiTestHelper.register;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.subtrack.notification.EmailSender;
import com.subtrack.security.RateLimitFilter;
import com.subtrack.support.AbstractIntegrationTest;
import com.subtrack.support.ApiTestHelper.TestUser;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Dashboard numbers against a known seeded data set (project plan §3.3). */
@AutoConfigureMockMvc
class DashboardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @MockitoBean
    private EmailSender emailSender;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @BeforeEach
    void resetLimits() {
        rateLimitFilter.reset();
    }

    @Test
    void summaryBoardAndMarkingFromTheBoard() throws Exception {
        TestUser user = register(mvc);
        create(user, "Netflix", "OTT", "600", "MONTHLY", today.plusDays(10));
        Integer airtel = create(user, "Airtel", "INTERNET", "500", "MONTHLY", today.minusDays(2));
        create(user, "Star Health", "INSURANCE", "12000", "YEARLY", today.plusDays(100));

        mvc.perform(user.auth(get("/api/dashboard/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCount").value(3))
                .andExpect(jsonPath("$.monthlyCommitment").value(2100.0))   // 600 + 500 + 12000/12
                .andExpect(jsonPath("$.dueIn30DaysCount").value(1))
                .andExpect(jsonPath("$.overdueCount").value(1))
                .andExpect(jsonPath("$.overdueAmount").value(500.0));

        var board = mvc.perform(user.auth(get("/api/dashboard/status-board")))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].name", contains("Airtel", "Netflix", "Star Health")))
                .andExpect(jsonPath("$[0].attention.displayStatus").value("OVERDUE"))
                .andReturn();
        Integer periodId = read(board, "$[0].attention.id");

        // Quick action from the board: mark Airtel paid
        mvc.perform(user.json(patch("/api/subscriptions/" + airtel + "/periods/" + periodId), "{\"status\":\"PAID\"}"))
                .andExpect(status().isOk());

        mvc.perform(user.auth(get("/api/dashboard/summary")))
                .andExpect(jsonPath("$.overdueCount").value(0))
                .andExpect(jsonPath("$.paidThisMonthCount").value(1))
                .andExpect(jsonPath("$.paidThisMonthAmount").value(500.0));

        mvc.perform(user.auth(get("/api/dashboard/status-board").param("paymentStatus", "OVERDUE")))
                .andExpect(jsonPath("$", hasSize(0)));

        // "Paid" tab: the bill just paid shows up for this month
        mvc.perform(user.auth(get("/api/dashboard/paid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.total").value(500.0))
                .andExpect(jsonPath("$.items[0].name").value("Airtel"))
                .andExpect(jsonPath("$.items[0].period.paymentStatus").value("PAID"));
        mvc.perform(user.auth(get("/api/dashboard/paid").param("month", today.minusYears(1).toString().substring(0, 7))))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(user.auth(get("/api/dashboard/paid").param("month", "not-a-month")))
                .andExpect(status().isBadRequest());

        mvc.perform(user.auth(get("/api/dashboard/category-breakdown")))
                .andExpect(jsonPath("$[0].category").value("INSURANCE"))
                .andExpect(jsonPath("$", hasSize(3)));

        mvc.perform(user.auth(get("/api/dashboard/trend").param("months", "6")))
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[5].paidCount").value(1));

        // Airtel was just paid, so its next due date (about a month after the old one) is upcoming too
        mvc.perform(user.auth(get("/api/dashboard/upcoming").param("days", "30")))
                .andExpect(jsonPath("$[*].name", contains("Netflix", "Airtel")));
    }

    @Test
    void dashboardIsPerUser() throws Exception {
        TestUser alice = register(mvc);
        TestUser bob = register(mvc);
        create(alice, "Netflix", "OTT", "600", "MONTHLY", today.plusDays(10));

        mvc.perform(bob.auth(get("/api/dashboard/status-board"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(bob.auth(get("/api/dashboard/summary"))).andExpect(jsonPath("$.activeCount").value(0));
    }

    private Integer create(TestUser user, String name, String category, String amount, String cycle, LocalDate due)
            throws Exception {
        String body = """
                {"name":"%s","category":"%s","amount":%s,"billingCycle":"%s","nextDueDate":"%s","autoRenew":false}
                """.formatted(name, category, amount, cycle, due);
        return read(mvc.perform(user.json(post("/api/subscriptions"), body)).andExpect(status().isCreated()).andReturn(),
                "$.id");
    }
}
