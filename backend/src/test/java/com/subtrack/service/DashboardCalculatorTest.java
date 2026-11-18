package com.subtrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.subtrack.dto.dashboard.DashboardDtos.CategorySlice;
import com.subtrack.dto.dashboard.DashboardDtos.StatusRow;
import com.subtrack.dto.dashboard.DashboardDtos.Summary;
import com.subtrack.dto.dashboard.DashboardDtos.TrendPoint;
import com.subtrack.entity.BillingPeriod;
import com.subtrack.entity.Subscription;
import com.subtrack.enums.BillingCycle;
import com.subtrack.enums.Category;
import com.subtrack.enums.PaymentStatus;
import com.subtrack.enums.PeriodDisplayStatus;
import com.subtrack.enums.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A known data set, today = 16 Nov 2026:
 * <pre>
 * Netflix   OTT       ₹649 monthly  manual   paid Oct + Nov, next 05 Dec (upcoming)
 * Airtel    INTERNET  ₹499 monthly  manual   10 Nov unpaid (overdue)
 * Spotify   OTT       ₹119 monthly  auto     01 Nov unconfirmed, next 01 Dec
 * Star      INSURANCE ₹18000 yearly  manual   next 01 Mar 2027
 * Jio       MOBILE    ₹299 monthly  CANCELLED, 02 Nov missed
 * Gym       OTHER     ₹3000 quarterly PAUSED, 12 Nov unpaid (overdue)
 * </pre>
 */
class DashboardCalculatorTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-16");

    private final List<Subscription> subs = new ArrayList<>();
    private final List<BillingPeriod> periods = new ArrayList<>();
    private DashboardCalculator calc;

    @BeforeEach
    void seed() {
        Subscription netflix = sub(1, "Netflix", Category.OTT, "649", BillingCycle.MONTHLY, "2026-12-05",
                SubscriptionStatus.ACTIVE, false);
        paid(netflix, "2026-10-05", "2026-10-04", "649");
        paid(netflix, "2026-11-05", "2026-11-06", "649");
        pending(netflix, "2026-12-05");

        Subscription airtel = sub(2, "Airtel", Category.INTERNET, "499", BillingCycle.MONTHLY, "2026-11-10",
                SubscriptionStatus.ACTIVE, false);
        pending(airtel, "2026-11-10");

        Subscription spotify = sub(3, "Spotify", Category.OTT, "119", BillingCycle.MONTHLY, "2026-12-01",
                SubscriptionStatus.ACTIVE, true);
        pending(spotify, "2026-11-01");
        pending(spotify, "2026-12-01");

        Subscription star = sub(4, "Star Health", Category.INSURANCE, "18000", BillingCycle.YEARLY, "2027-03-01",
                SubscriptionStatus.ACTIVE, false);
        pending(star, "2027-03-01");

        Subscription jio = sub(5, "Jio", Category.MOBILE_RECHARGE, "299", BillingCycle.MONTHLY, "2026-12-02",
                SubscriptionStatus.CANCELLED, false);
        missed(jio, "2026-11-02");
        pending(jio, "2026-12-02");

        Subscription gym = sub(6, "Gym", Category.OTHER, "3000", BillingCycle.QUARTERLY, "2026-11-12",
                SubscriptionStatus.PAUSED, false);
        pending(gym, "2026-11-12");

        calc = new DashboardCalculator(subs, periods, TODAY);
    }

    @Test
    void summaryTiles() {
        Summary s = calc.summary();

        assertThat(s.activeCount()).isEqualTo(4);
        assertThat(s.monthlyCommitment()).isEqualByComparingTo("2767.00"); // 649 + 499 + 119 + 18000/12
        assertThat(s.yearlyCommitment()).isEqualByComparingTo("33204.00");
        assertThat(s.dueIn30DaysCount()).isEqualTo(2);                     // Netflix 05 Dec, Spotify 01 Dec
        assertThat(s.dueIn30DaysAmount()).isEqualByComparingTo("768");
        assertThat(s.overdueCount()).isEqualTo(2);                         // Airtel + paused Gym
        assertThat(s.overdueAmount()).isEqualByComparingTo("3499");
        assertThat(s.awaitingConfirmationCount()).isEqualTo(1);            // Spotify 01 Nov
        assertThat(s.paidThisMonthCount()).isEqualTo(1);                   // paid on 06 Nov
        assertThat(s.paidThisMonthAmount()).isEqualByComparingTo("649");
        assertThat(s.missedThisMonthCount()).isEqualTo(1);                 // Jio, even though cancelled since
    }

    @Test
    void boardPutsWhatNeedsActionFirst() {
        List<StatusRow> rows = calc.statusBoard().stream().sorted(DashboardCalculator.byUrgency()).toList();

        assertThat(rows).extracting(StatusRow::name)
                .containsExactly("Airtel", "Gym", "Spotify", "Netflix", "Star Health", "Jio");
        assertThat(rows.get(0).attention().displayStatus()).isEqualTo(PeriodDisplayStatus.OVERDUE);
        assertThat(rows.get(5).attention()).isNull(); // cancelled: nothing to do
    }

    @Test
    void attentionIsTheOldestUnmarkedPastPeriod() {
        StatusRow spotify = row("Spotify");

        assertThat(spotify.attention().dueDate()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(spotify.attention().displayStatus()).isEqualTo(PeriodDisplayStatus.AWAITING_CONFIRMATION);
        assertThat(spotify.unmarkedPastCount()).isEqualTo(1);
        assertThat(spotify.daysLeft()).isEqualTo(15);
    }

    @Test
    void attentionIsTheCurrentPeriodWhenNothingIsLate() {
        StatusRow netflix = row("Netflix");

        assertThat(netflix.attention().dueDate()).isEqualTo(LocalDate.parse("2026-12-05"));
        assertThat(netflix.attention().displayStatus()).isEqualTo(PeriodDisplayStatus.UPCOMING);
        assertThat(netflix.lastClosed().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(netflix.lastClosed().paidOn()).isEqualTo(LocalDate.parse("2026-11-06"));
        assertThat(row("Jio").lastClosed().paymentStatus()).isEqualTo(PaymentStatus.MISSED);
    }

    @Test
    void categoryBreakdownCountsOnlyActive() {
        assertThat(calc.categoryBreakdown()).extracting(CategorySlice::category, CategorySlice::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Category.INSURANCE, 1),
                        org.assertj.core.groups.Tuple.tuple(Category.OTT, 2),
                        org.assertj.core.groups.Tuple.tuple(Category.INTERNET, 1));
        assertThat(calc.categoryBreakdown().get(1).monthlyAmount()).isEqualByComparingTo("768");
    }

    @Test
    void trendCoversEachMonthIncludingEmptyOnes() {
        List<TrendPoint> trend = calc.trend(3);

        assertThat(trend).extracting(TrendPoint::month).containsExactly("2026-09", "2026-10", "2026-11");
        assertThat(trend.get(0).paidAmount()).isEqualByComparingTo("0");
        assertThat(trend.get(1).paidAmount()).isEqualByComparingTo("649");
        assertThat(trend.get(2).paidCount()).isEqualTo(1);
        assertThat(trend.get(2).missedCount()).isEqualTo(1);
    }

    @Test
    void paidInMonthListsPaymentsByPaidOnDate() {
        var nov = calc.paidIn(java.time.YearMonth.of(2026, 11));
        assertThat(nov.month()).isEqualTo("2026-11");
        assertThat(nov.count()).isEqualTo(1);
        assertThat(nov.total()).isEqualByComparingTo("649");
        assertThat(nov.items().get(0).name()).isEqualTo("Netflix");
        assertThat(nov.items().get(0).period().paidOn()).isEqualTo(LocalDate.parse("2026-11-06"));

        // The October cycle was paid on 04 Oct, so it belongs to October
        assertThat(calc.paidIn(java.time.YearMonth.of(2026, 10)).count()).isEqualTo(1);
        assertThat(calc.paidIn(java.time.YearMonth.of(2026, 9)).items()).isEmpty();
        assertThat(calc.paidIn(java.time.YearMonth.of(2026, 9)).total()).isEqualByComparingTo("0");
    }

    // ---- helpers -------------------------------------------------------------

    private StatusRow row(String name) {
        return calc.statusBoard().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }

    private Subscription sub(long id, String name, Category cat, String amount, BillingCycle cycle, String nextDue,
                             SubscriptionStatus status, boolean autoRenew) {
        Subscription s = new Subscription();
        s.setId(id);
        s.setName(name);
        s.setCategory(cat);
        s.setAmount(new BigDecimal(amount));
        s.setBillingCycle(cycle);
        s.setNextDueDate(LocalDate.parse(nextDue));
        s.setStartDate(LocalDate.parse(nextDue));
        s.setStatus(status);
        s.setAutoRenew(autoRenew);
        subs.add(s);
        return s;
    }

    private BillingPeriod pending(Subscription s, String due) {
        BillingPeriod p = new BillingPeriod(s, LocalDate.parse(due), s.getAmount());
        p.setId((long) periods.size() + 1);
        periods.add(p);
        return p;
    }

    private void paid(Subscription s, String due, String paidOn, String amount) {
        BillingPeriod p = pending(s, due);
        p.setPaymentStatus(PaymentStatus.PAID);
        p.setPaidOn(LocalDate.parse(paidOn));
        p.setAmountPaid(new BigDecimal(amount));
    }

    private void missed(Subscription s, String due) {
        pending(s, due).setPaymentStatus(PaymentStatus.MISSED);
    }
}
