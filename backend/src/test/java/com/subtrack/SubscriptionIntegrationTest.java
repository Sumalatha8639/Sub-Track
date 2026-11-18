package com.subtrack;

import static com.subtrack.support.ApiTestHelper.read;
import static com.subtrack.support.ApiTestHelper.register;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SubscriptionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RateLimitFilter rateLimitFilter;

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
    void createGetUpdateAndSoftDelete() throws Exception {
        TestUser user = register(mvc);
        LocalDate due = today.plusDays(10);

        var created = mvc.perform(user.json(post("/api/subscriptions"), body("Netflix", "OTT", "649", "MONTHLY", due)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nextDueDate").value(due.toString()))
                .andExpect(jsonPath("$.daysLeft").value(10))
                .andExpect(jsonPath("$.reminderDays", contains(7, 3, 1)))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        Integer id = read(created, "$.id");

        // First billing period was opened
        Integer periods = jdbc.queryForObject(
                "SELECT COUNT(*) FROM billing_periods WHERE subscription_id = ? AND payment_status = 'PENDING'",
                Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(periods).isEqualTo(1);

        mvc.perform(user.auth(get("/api/subscriptions/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Netflix"));

        mvc.perform(user.json(put("/api/subscriptions/" + id), body("Netflix Premium", "OTT", "799", "MONTHLY", due)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Netflix Premium"))
                .andExpect(jsonPath("$.amount").value(799));

        mvc.perform(user.auth(delete("/api/subscriptions/" + id))).andExpect(status().isNoContent());
        mvc.perform(user.auth(get("/api/subscriptions/" + id))).andExpect(status().isNotFound());
        mvc.perform(user.auth(get("/api/subscriptions"))).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void usersCannotSeeEachOthersSubscriptions() throws Exception {
        TestUser alice = register(mvc);
        TestUser bob = register(mvc);
        var created = mvc.perform(alice.json(post("/api/subscriptions"),
                        body("Jio", "MOBILE_RECHARGE", "299", "MONTHLY", today.plusDays(5))))
                .andExpect(status().isCreated()).andReturn();
        Integer id = read(created, "$.id");

        mvc.perform(bob.auth(get("/api/subscriptions/" + id))).andExpect(status().isNotFound());
        mvc.perform(bob.json(put("/api/subscriptions/" + id),
                body("Hacked", "OTHER", "1", "MONTHLY", today))).andExpect(status().isNotFound());
        mvc.perform(bob.auth(delete("/api/subscriptions/" + id))).andExpect(status().isNotFound());
        mvc.perform(bob.auth(get("/api/subscriptions"))).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listFiltersSearchesSortsAndPages() throws Exception {
        TestUser user = register(mvc);
        mvc.perform(user.json(post("/api/subscriptions"), body("Netflix", "OTT", "649", "MONTHLY", today.plusDays(3))));
        mvc.perform(user.json(post("/api/subscriptions"), body("Hotstar", "OTT", "299", "MONTHLY", today.plusDays(20))));
        mvc.perform(user.json(post("/api/subscriptions"), body("Star Health", "INSURANCE", "18500", "YEARLY", today.plusDays(60))));
        mvc.perform(user.json(post("/api/subscriptions"), body("100% Cloud", "AI_TOOL", "1700", "MONTHLY", today.plusDays(8))));

        mvc.perform(user.auth(get("/api/subscriptions")))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].name").value("Netflix")); // soonest first

        mvc.perform(user.auth(get("/api/subscriptions").param("category", "OTT")))
                .andExpect(jsonPath("$.content", hasSize(2)));

        mvc.perform(user.auth(get("/api/subscriptions").param("dueWithinDays", "10")))
                .andExpect(jsonPath("$.content[*].name", contains("Netflix", "100% Cloud")));

        mvc.perform(user.auth(get("/api/subscriptions").param("q", "star")))
                .andExpect(jsonPath("$.content[*].name", contains("Hotstar", "Star Health")));

        mvc.perform(user.auth(get("/api/subscriptions").param("q", "%")))
                .andExpect(jsonPath("$.content[*].name", contains("100% Cloud")));

        mvc.perform(user.auth(get("/api/subscriptions").param("sort", "amount").param("direction", "desc")
                        .param("size", "2")))
                .andExpect(jsonPath("$.content[*].name", contains("Star Health", "100% Cloud")))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void statusChangesAndReminders() throws Exception {
        TestUser user = register(mvc);
        var created = mvc.perform(user.json(post("/api/subscriptions"),
                body("Star Health", "INSURANCE", "18500", "YEARLY", today.plusDays(40)))).andReturn();
        Integer id = read(created, "$.id");

        mvc.perform(user.json(patch("/api/subscriptions/" + id + "/status"), "{\"status\":\"PAUSED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(user.json(patch("/api/subscriptions/" + id + "/status"), "{\"status\":\"EXPIRED\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(user.json(put("/api/subscriptions/" + id + "/reminders"), "{\"days\":[45,10,10,1]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reminderDays", contains(45, 10, 1)));
        mvc.perform(user.json(put("/api/subscriptions/" + id + "/reminders"), "{\"days\":[0,120]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validationErrors() throws Exception {
        TestUser user = register(mvc);

        mvc.perform(user.json(post("/api/subscriptions"),
                        "{\"name\":\"\",\"category\":\"OTT\",\"amount\":-5,\"billingCycle\":\"MONTHLY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.nextDueDate").exists());

        mvc.perform(user.json(post("/api/subscriptions"), body("X", "NOT_A_CATEGORY", "10", "MONTHLY", today)))
                .andExpect(status().isBadRequest());

        mvc.perform(user.json(post("/api/subscriptions"), bodyAutoRenew("Course", "OTHER", "4999", "ONE_TIME", today)))
                .andExpect(status().isBadRequest());

        mvc.perform(user.auth(get("/api/subscriptions").param("size", "500")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").exists());

        // A request without autoRenew is fine (defaults to false)
        mvc.perform(user.json(post("/api/subscriptions"), """
                        {"name":"Jio","category":"MOBILE_RECHARGE","amount":299,"billingCycle":"MONTHLY","nextDueDate":"%s"}
                        """.formatted(today.plusDays(3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autoRenew").value(false));
    }

    private static String body(String name, String category, String amount, String cycle, LocalDate due) {
        return """
                {"name":"%s","category":"%s","amount":%s,"billingCycle":"%s","nextDueDate":"%s","autoRenew":false}
                """.formatted(name, category, amount, cycle, due);
    }

    private static String bodyAutoRenew(String name, String category, String amount, String cycle, LocalDate due) {
        return body(name, category, amount, cycle, due).replace("\"autoRenew\":false", "\"autoRenew\":true");
    }
}
