package com.subtrack.demo;

import com.subtrack.entity.BillingPeriod;
import com.subtrack.entity.ReminderSetting;
import com.subtrack.entity.Subscription;
import com.subtrack.entity.User;
import com.subtrack.enums.BillingCycle;
import com.subtrack.enums.Category;
import com.subtrack.enums.PaymentStatus;
import com.subtrack.enums.SubscriptionStatus;
import com.subtrack.repository.BillingPeriodRepository;
import com.subtrack.repository.SubscriptionRepository;
import com.subtrack.repository.UserRepository;
import com.subtrack.util.DueDateCalculator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a demo account with history that shows every status on the dashboard.
 * Only with the {@code demo} profile: {@code mvnw spring-boot:run -Dspring-boot.run.profiles=local,demo}.
 * Runs once; does nothing if the demo user already exists.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    public static final String EMAIL = "demo@subtrack.app";
    public static final String PASSWORD = "demo-password";
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final UserRepository users;
    private final SubscriptionRepository subscriptions;
    private final BillingPeriodRepository periods;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    /** When true (the demo default), the demo account is rebuilt fresh on every start. */
    @Value("${app.demo.reset-on-start:true}")
    private boolean resetOnStart;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long existing = users.findByEmailIgnoreCase(EMAIL).map(User::getId).orElse(null);
        if (existing != null) {
            if (!resetOnStart) {
                log.info("Demo user already exists ({}); keeping its data", EMAIL);
                return;
            }
            deleteDemoUser(existing);
        }
        LocalDate today = LocalDate.now(clock.withZone(ZONE));

        User user = new User();
        user.setName("Demo User");
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setTimezone(ZONE.getId());
        user.setEmailVerified(true);
        users.save(user);

        // past = statuses of due dates already passed: P paid (day before due), T paid today, M missed, U unmarked
        seed(user, "Netflix", Category.OTT, "649", BillingCycle.MONTHLY, today.minusMonths(6).plusDays(3), false, "PPPPPP", SubscriptionStatus.ACTIVE);
        seed(user, "Jio Recharge", Category.MOBILE_RECHARGE, "299", BillingCycle.MONTHLY, today.minusMonths(3).minusDays(2), false, "PPPU", SubscriptionStatus.ACTIVE);
        seed(user, "Spotify Premium", Category.OTT, "119", BillingCycle.MONTHLY, today.minusMonths(3).minusDays(5), true, "PPPU", SubscriptionStatus.ACTIVE);
        seed(user, "Star Health Insurance", Category.INSURANCE, "18500", BillingCycle.YEARLY, today.minusYears(1).plusDays(20), false, "P", SubscriptionStatus.ACTIVE);
        seed(user, "ChatGPT Plus", Category.AI_TOOL, "1999", BillingCycle.MONTHLY, today.minusMonths(2).plusDays(12), true, "PP", SubscriptionStatus.ACTIVE);
        seed(user, "Airtel Xstream Fiber", Category.INTERNET, "999", BillingCycle.MONTHLY, today.minusMonths(5).plusDays(9), false, "PMPPP", SubscriptionStatus.ACTIVE);
        seed(user, "LIC Policy", Category.INSURANCE, "7200", BillingCycle.HALF_YEARLY, today.minusMonths(6).plusDays(45), false, "P", SubscriptionStatus.ACTIVE);
        seed(user, "Cult.fit", Category.OTHER, "4500", BillingCycle.QUARTERLY, today.minusMonths(3).plusDays(1), false, "P", SubscriptionStatus.PAUSED);
        seed(user, "Hotstar", Category.OTT, "1499", BillingCycle.YEARLY, today.minusMonths(2), false, "M", SubscriptionStatus.CANCELLED);
        seed(user, "Electricity (BESCOM)", Category.UTILITY, "1850", BillingCycle.MONTHLY, today.plusDays(1), false, "", SubscriptionStatus.ACTIVE);
        // So "Paid this month" and "Missed this month" have something to show
        seed(user, "Amazon Prime", Category.OTT, "299", BillingCycle.MONTHLY, today.minusMonths(1).minusDays(3), false, "PT", SubscriptionStatus.ACTIVE);
        seed(user, "Google One", Category.AI_TOOL, "130", BillingCycle.MONTHLY, today.minusMonths(1).minusDays(1), false, "PM", SubscriptionStatus.ACTIVE);

        log.info("Demo data created. Log in as {} / {}", EMAIL, PASSWORD);
    }

    /** Removes the demo account and everything under it, children first. */
    private void deleteDemoUser(Long userId) {
        String subs = "SELECT id FROM subscriptions WHERE user_id = ?";
        String periodsOfUser = "SELECT id FROM billing_periods WHERE subscription_id IN (" + subs + ")";
        jdbc.update("DELETE FROM notification_log WHERE billing_period_id IN (" + periodsOfUser + ")", userId);
        jdbc.update("DELETE FROM billing_period_history WHERE billing_period_id IN (" + periodsOfUser + ")", userId);
        jdbc.update("DELETE FROM billing_periods WHERE subscription_id IN (" + subs + ")", userId);
        jdbc.update("DELETE FROM reminder_settings WHERE subscription_id IN (" + subs + ")", userId);
        jdbc.update("DELETE FROM subscriptions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_tokens WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        users.flush();
        log.info("Reset the demo account");
    }

    private void seed(User user, String name, Category category, String amount, BillingCycle cycle, LocalDate anchor,
                      boolean autoRenew, String past, SubscriptionStatus status) {
        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setName(name);
        sub.setCategory(category);
        sub.setAmount(new BigDecimal(amount));
        sub.setBillingCycle(cycle);
        sub.setStartDate(anchor);
        sub.setAutoRenew(autoRenew);
        sub.setStatus(status);
        cycle.defaultReminderDays().forEach(d -> sub.getReminderSettings().add(new ReminderSetting(sub, d)));

        // A manual subscription whose last past cycle is unmarked stays on that cycle (it's overdue);
        // an auto-renew one has already moved on (that cycle is awaiting confirmation).
        boolean stuckOnLast = !autoRenew && past.endsWith("U");
        int current = stuckOnLast ? past.length() - 1 : past.length();
        sub.setCyclesElapsed(current);
        sub.setNextDueDate(DueDateCalculator.dueDate(anchor, cycle, current));
        subscriptions.save(sub);

        for (int k = 0; k < past.length(); k++) {
            LocalDate due = DueDateCalculator.dueDate(anchor, cycle, k);
            BillingPeriod p = new BillingPeriod(sub, due, sub.getAmount());
            switch (past.charAt(k)) {
                case 'P' -> {
                    p.setPaymentStatus(PaymentStatus.PAID);
                    p.setPaidOn(due.minusDays(1));
                    p.setAmountPaid(sub.getAmount());
                    p.setMarkedAt(Instant.now(clock));
                }
                case 'T' -> {
                    p.setPaymentStatus(PaymentStatus.PAID);
                    p.setPaidOn(LocalDate.now(clock.withZone(ZONE)));
                    p.setAmountPaid(sub.getAmount());
                    p.setMarkedAt(Instant.now(clock));
                }
                case 'M' -> {
                    p.setPaymentStatus(PaymentStatus.MISSED);
                    p.setMarkedAt(Instant.now(clock));
                }
                default -> p.setPaymentStatus(PaymentStatus.PENDING);
            }
            periods.save(p);
        }
        if (!stuckOnLast) {
            periods.save(new BillingPeriod(sub, sub.getNextDueDate(), sub.getAmount()));
        }
    }
}
