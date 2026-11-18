package com.subtrack.service;

import com.subtrack.dto.dashboard.DashboardDtos.CategorySlice;
import com.subtrack.dto.dashboard.DashboardDtos.LastClosed;
import com.subtrack.dto.dashboard.DashboardDtos.PaidInMonth;
import com.subtrack.dto.dashboard.DashboardDtos.PaidItem;
import com.subtrack.dto.dashboard.DashboardDtos.StatusRow;
import com.subtrack.dto.dashboard.DashboardDtos.Summary;
import com.subtrack.dto.dashboard.DashboardDtos.TrendPoint;
import com.subtrack.dto.period.BillingPeriodResponse;
import com.subtrack.entity.BillingPeriod;
import com.subtrack.entity.Subscription;
import com.subtrack.enums.Category;
import com.subtrack.enums.PaymentStatus;
import com.subtrack.enums.PeriodDisplayStatus;
import com.subtrack.enums.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure dashboard maths over one user's (non-deleted) subscriptions and billing periods.
 * No database access, so every number is easy to unit test.
 */
public final class DashboardCalculator {

    /** Subscriptions whose unpaid periods still count as needing action. */
    private static final Set<SubscriptionStatus> OPEN = EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAUSED);

    /** Default board order: what needs action first (project plan §3.3 "overdue first"). */
    private static final Map<PeriodDisplayStatus, Integer> URGENCY = Map.of(
            PeriodDisplayStatus.OVERDUE, 0,
            PeriodDisplayStatus.AWAITING_CONFIRMATION, 1,
            PeriodDisplayStatus.DUE_TODAY, 2,
            PeriodDisplayStatus.DUE_SOON, 3,
            PeriodDisplayStatus.UPCOMING, 4,
            PeriodDisplayStatus.PAID, 5,
            PeriodDisplayStatus.MISSED, 6);

    private final List<Subscription> subscriptions;
    private final Map<Long, List<BillingPeriod>> periodsBySub;
    private final LocalDate today;

    public DashboardCalculator(List<Subscription> subscriptions, List<BillingPeriod> periods, LocalDate today) {
        this.subscriptions = subscriptions;
        this.periodsBySub = periods.stream().collect(Collectors.groupingBy(p -> p.getSubscription().getId()));
        this.today = today;
    }

    // ---- summary -------------------------------------------------------------------

    public Summary summary() {
        YearMonth thisMonth = YearMonth.from(today);
        int active = 0;
        BigDecimal monthly = BigDecimal.ZERO;
        int dueCount = 0;
        BigDecimal dueAmount = BigDecimal.ZERO;
        int overdueCount = 0;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        int awaiting = 0;
        int paidCount = 0;
        BigDecimal paidAmount = BigDecimal.ZERO;
        int missedCount = 0;

        for (Subscription s : subscriptions) {
            if (s.getStatus() == SubscriptionStatus.ACTIVE) {
                active++;
                monthly = monthly.add(s.getBillingCycle().monthlyAmount(s.getAmount()));
            }
            for (BillingPeriod p : periodsOf(s)) {
                switch (p.getPaymentStatus()) {
                    case PAID -> {
                        if (p.getPaidOn() != null && YearMonth.from(p.getPaidOn()).equals(thisMonth)) {
                            paidCount++;
                            paidAmount = paidAmount.add(p.getAmountPaid());
                        }
                    }
                    case MISSED -> {
                        if (YearMonth.from(p.getDueDate()).equals(thisMonth)) {
                            missedCount++;
                        }
                    }
                    case PENDING -> {
                        if (!OPEN.contains(s.getStatus())) {
                            continue;
                        }
                        long daysLeft = ChronoUnit.DAYS.between(today, p.getDueDate());
                        if (daysLeft < 0) {
                            if (s.isAutoRenew()) {
                                awaiting++;
                            } else {
                                overdueCount++;
                                overdueAmount = overdueAmount.add(p.getAmountDue());
                            }
                        } else if (daysLeft <= 30 && s.getStatus() == SubscriptionStatus.ACTIVE) {
                            dueCount++;
                            dueAmount = dueAmount.add(p.getAmountDue());
                        }
                    }
                }
            }
        }
        return new Summary(active, dueCount, dueAmount, overdueCount, overdueAmount, awaiting,
                paidCount, paidAmount, missedCount, monthly, monthly.multiply(BigDecimal.valueOf(12)));
    }

    // ---- status board ----------------------------------------------------------------

    public List<StatusRow> statusBoard() {
        return subscriptions.stream().map(this::row).toList();
    }

    StatusRow row(Subscription s) {
        List<BillingPeriod> periods = periodsOf(s);
        BillingPeriod attention = null;
        int unmarkedPast = 0;
        if (OPEN.contains(s.getStatus())) {
            List<BillingPeriod> pending = periods.stream()
                    .filter(p -> p.getPaymentStatus() == PaymentStatus.PENDING)
                    .sorted(Comparator.comparing(BillingPeriod::getDueDate))
                    .toList();
            unmarkedPast = (int) pending.stream().filter(p -> p.getDueDate().isBefore(today)).count();
            attention = pending.stream()
                    .filter(p -> p.getDueDate().isBefore(today))
                    .findFirst()
                    .orElseGet(() -> pending.stream()
                            .filter(p -> p.getDueDate().equals(s.getNextDueDate()))
                            .findFirst()
                            .orElse(null));
        }
        LastClosed last = periods.stream()
                .filter(p -> p.getPaymentStatus() != PaymentStatus.PENDING)
                .max(Comparator.comparing(BillingPeriod::getDueDate))
                .map(p -> new LastClosed(p.getDueDate(), p.getPaymentStatus(), p.getPaidOn(), p.getAmountPaid()))
                .orElse(null);
        return new StatusRow(s.getId(), s.getName(), s.getCategory(), s.getAmount(), s.getBillingCycle(),
                s.getNextDueDate(), ChronoUnit.DAYS.between(today, s.getNextDueDate()), s.getStatus(),
                s.isAutoRenew(), attention == null ? null : BillingPeriodResponse.from(attention, today),
                unmarkedPast, last);
    }

    /** Most urgent first; rows without an action (cancelled/expired) last; then by due date. */
    public static Comparator<StatusRow> byUrgency() {
        return Comparator.<StatusRow>comparingInt(r -> r.attention() == null
                        ? Integer.MAX_VALUE
                        : URGENCY.get(r.attention().displayStatus()))
                .thenComparing(r -> r.attention() == null ? r.nextDueDate() : r.attention().dueDate())
                .thenComparing(StatusRow::name, String.CASE_INSENSITIVE_ORDER);
    }

    // ---- category breakdown ----------------------------------------------------------

    /** Monthly commitment of ACTIVE subscriptions per category, largest first. */
    public List<CategorySlice> categoryBreakdown() {
        Map<Category, BigDecimal> amounts = new EnumMap<>(Category.class);
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        for (Subscription s : subscriptions) {
            if (s.getStatus() != SubscriptionStatus.ACTIVE) {
                continue;
            }
            amounts.merge(s.getCategory(), s.getBillingCycle().monthlyAmount(s.getAmount()), BigDecimal::add);
            counts.merge(s.getCategory(), 1, Integer::sum);
        }
        return amounts.entrySet().stream()
                .map(e -> new CategorySlice(e.getKey(), counts.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(CategorySlice::monthlyAmount).reversed())
                .toList();
    }

    // ---- paid in a month -----------------------------------------------------------------

    /** Every period marked PAID with a paid-on date in {@code month}, newest payment first. */
    public PaidInMonth paidIn(YearMonth month) {
        Map<Long, Subscription> byId = subscriptions.stream()
                .collect(Collectors.toMap(Subscription::getId, s -> s));
        List<PaidItem> items = periodsBySub.values().stream()
                .flatMap(List::stream)
                .filter(p -> p.getPaymentStatus() == PaymentStatus.PAID && p.getPaidOn() != null)
                .filter(p -> YearMonth.from(p.getPaidOn()).equals(month))
                .filter(p -> byId.containsKey(p.getSubscription().getId()))
                .sorted(Comparator.comparing(BillingPeriod::getPaidOn).reversed()
                        .thenComparing(p -> byId.get(p.getSubscription().getId()).getName(), String.CASE_INSENSITIVE_ORDER))
                .map(p -> {
                    Subscription s = byId.get(p.getSubscription().getId());
                    return new PaidItem(s.getId(), s.getName(), s.getCategory(), s.getBillingCycle(), s.getStatus(),
                            BillingPeriodResponse.from(p, today));
                })
                .toList();
        BigDecimal total = items.stream()
                .map(i -> i.period().amountPaid())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PaidInMonth(month.toString(), total, items.size(), items);
    }

    // ---- trend --------------------------------------------------------------------------

    /** The last {@code months} calendar months, oldest first, including the current one. */
    public List<TrendPoint> trend(int months) {
        YearMonth end = YearMonth.from(today);
        YearMonth start = end.minusMonths(months - 1L);
        Map<YearMonth, BigDecimal> paid = new LinkedHashMap<>();
        Map<YearMonth, Integer> paidCount = new LinkedHashMap<>();
        Map<YearMonth, Integer> missed = new LinkedHashMap<>();
        for (List<BillingPeriod> list : periodsBySub.values()) {
            for (BillingPeriod p : list) {
                if (p.getPaymentStatus() == PaymentStatus.PAID && p.getPaidOn() != null) {
                    YearMonth m = YearMonth.from(p.getPaidOn());
                    paid.merge(m, p.getAmountPaid(), BigDecimal::add);
                    paidCount.merge(m, 1, Integer::sum);
                } else if (p.getPaymentStatus() == PaymentStatus.MISSED) {
                    missed.merge(YearMonth.from(p.getDueDate()), 1, Integer::sum);
                }
            }
        }
        List<TrendPoint> points = new ArrayList<>();
        for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            points.add(new TrendPoint(m.toString(), paid.getOrDefault(m, BigDecimal.ZERO.setScale(2)),
                    paidCount.getOrDefault(m, 0), missed.getOrDefault(m, 0)));
        }
        return points;
    }

    private List<BillingPeriod> periodsOf(Subscription s) {
        return periodsBySub.getOrDefault(s.getId(), List.of());
    }
}
