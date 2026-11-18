package com.subtrack.service;

import static com.subtrack.repository.SubscriptionSpecs.category;
import static com.subtrack.repository.SubscriptionSpecs.dueOnOrBefore;
import static com.subtrack.repository.SubscriptionSpecs.nameContains;
import static com.subtrack.repository.SubscriptionSpecs.ownedBy;
import static com.subtrack.repository.SubscriptionSpecs.status;

import com.subtrack.dto.common.PageResponse;
import com.subtrack.dto.subscription.SubscriptionRequest;
import com.subtrack.dto.subscription.SubscriptionResponse;
import com.subtrack.entity.BillingPeriod;
import com.subtrack.entity.ReminderSetting;
import com.subtrack.entity.Subscription;
import com.subtrack.entity.User;
import com.subtrack.enums.BillingCycle;
import com.subtrack.enums.Category;
import com.subtrack.enums.PaymentStatus;
import com.subtrack.enums.SubscriptionStatus;
import com.subtrack.exception.BadRequestException;
import com.subtrack.exception.NotFoundException;
import com.subtrack.repository.BillingPeriodRepository;
import com.subtrack.repository.SubscriptionRepository;
import com.subtrack.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Subscription CRUD, always scoped to the logged-in user (project plan §3.1, §6). */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /** Due dates further back than this are almost certainly typos. */
    static final int MAX_DAYS_IN_PAST = 366;

    static final Set<String> SORTABLE = Set.of("name", "amount", "nextDueDate", "createdAt");

    /** Allowed manual status changes. EXPIRED is set only by the system. */
    private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> TRANSITIONS = Map.of(
            SubscriptionStatus.ACTIVE, EnumSet.of(SubscriptionStatus.PAUSED, SubscriptionStatus.CANCELLED),
            SubscriptionStatus.PAUSED, EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELLED),
            SubscriptionStatus.CANCELLED, EnumSet.of(SubscriptionStatus.ACTIVE),
            SubscriptionStatus.EXPIRED, EnumSet.noneOf(SubscriptionStatus.class));

    private final SubscriptionRepository subscriptions;
    private final BillingPeriodRepository periods;
    private final UserRepository users;
    private final UserTime userTime;
    private final Clock clock;

    /** List filters; all optional. */
    public record Filter(Category category, SubscriptionStatus status, Integer dueWithinDays, String q) {
    }

    @Transactional
    public SubscriptionResponse create(Long userId, SubscriptionRequest req) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        LocalDate today = userTime.today(user);
        validate(req, today);

        Subscription sub = new Subscription();
        sub.setUser(user);
        applyDetails(sub, req);
        sub.setStartDate(req.nextDueDate());
        sub.setCyclesElapsed(0);
        sub.setNextDueDate(req.nextDueDate());
        sub.setStatus(SubscriptionStatus.ACTIVE);
        List<Integer> days = req.reminderDays() != null ? req.reminderDays() : req.billingCycle().defaultReminderDays();
        replaceReminders(sub, days);
        subscriptions.save(sub);

        periods.save(new BillingPeriod(sub, sub.getNextDueDate(), sub.getAmount()));
        return toResponse(sub, today);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubscriptionResponse> list(Long userId, Filter filter, int page, int size,
                                                   String sortBy, String direction) {
        LocalDate today = userTime.today(userId);
        LocalDate dueBy = filter.dueWithinDays() == null ? null : today.plusDays(filter.dueWithinDays());

        Specification<Subscription> spec = Stream.of(
                        category(filter.category()), status(filter.status()),
                        dueOnOrBefore(dueBy), nameContains(filter.q()))
                .filter(Objects::nonNull)
                .reduce(ownedBy(userId), Specification::and);

        String property = SORTABLE.contains(sortBy) ? sortBy : "nextDueDate";
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(dir, property).and(Sort.by("id"));

        Page<Subscription> result = subscriptions.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort));
        return PageResponse.of(result, s -> toResponse(s, today));
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse get(Long userId, Long id) {
        return toResponse(load(userId, id), userTime.today(userId));
    }

    /**
     * Updates details. Changing the due date or billing cycle re-anchors the subscription and
     * moves the current (unpaid) billing period to the new date.
     */
    @Transactional
    public SubscriptionResponse update(Long userId, Long id, SubscriptionRequest req) {
        Subscription sub = load(userId, id);
        LocalDate today = userTime.today(sub.getUser());
        validate(req, today);
        if (sub.getStatus() == SubscriptionStatus.EXPIRED) {
            throw new BadRequestException("An expired subscription can't be edited");
        }

        LocalDate oldDue = sub.getNextDueDate();
        boolean reanchor = !req.nextDueDate().equals(oldDue) || req.billingCycle() != sub.getBillingCycle();
        applyDetails(sub, req);

        BillingPeriod current = periods.findBySubscriptionIdAndDueDate(sub.getId(), oldDue)
                .filter(p -> p.getPaymentStatus() == PaymentStatus.PENDING)
                .orElse(null);
        if (reanchor) {
            sub.setStartDate(req.nextDueDate());
            sub.setCyclesElapsed(0);
            sub.setNextDueDate(req.nextDueDate());
            if (current != null) {
                current.setDueDate(req.nextDueDate());
            } else {
                periods.save(new BillingPeriod(sub, sub.getNextDueDate(), sub.getAmount()));
            }
        }
        if (current != null) {
            current.setAmountDue(sub.getAmount());
        }
        subscriptions.flush();
        return toResponse(sub, today);
    }

    /** Soft delete: hidden everywhere, history kept. */
    @Transactional
    public void delete(Long userId, Long id) {
        load(userId, id).setDeletedAt(clock.instant());
    }

    @Transactional
    public SubscriptionResponse changeStatus(Long userId, Long id, SubscriptionStatus target) {
        Subscription sub = load(userId, id);
        if (sub.getStatus() != target) {
            if (!TRANSITIONS.get(sub.getStatus()).contains(target)) {
                throw new BadRequestException("Can't change status from " + sub.getStatus() + " to " + target);
            }
            sub.setStatus(target);
        }
        subscriptions.flush();
        return toResponse(sub, userTime.today(sub.getUser()));
    }

    @Transactional
    public SubscriptionResponse updateReminders(Long userId, Long id, List<Integer> days) {
        Subscription sub = load(userId, id);
        replaceReminders(sub, days);
        subscriptions.flush();
        return toResponse(sub, userTime.today(sub.getUser()));
    }

    // ---- helpers -------------------------------------------------------------

    Subscription load(Long userId, Long id) {
        return subscriptions.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new NotFoundException("Subscription not found"));
    }

    private static void validate(SubscriptionRequest req, LocalDate today) {
        if (req.autoRenewEnabled() && req.billingCycle() == BillingCycle.ONE_TIME) {
            throw new BadRequestException("One-time subscriptions can't auto-renew");
        }
        if (req.nextDueDate().isBefore(today.minusDays(MAX_DAYS_IN_PAST))) {
            throw new BadRequestException("Next due date is more than a year in the past");
        }
    }

    private static void applyDetails(Subscription sub, SubscriptionRequest req) {
        sub.setName(req.name().trim());
        sub.setCategory(req.category());
        sub.setAmount(req.amount());
        sub.setBillingCycle(req.billingCycle());
        sub.setAutoRenew(req.autoRenewEnabled());
        sub.setNotes(req.notes() == null || req.notes().isBlank() ? null : req.notes().trim());
    }

    /** Replaces reminder days, keeping existing rows that are still wanted (avoids unique-key clashes). */
    private static void replaceReminders(Subscription sub, Collection<Integer> days) {
        Set<Integer> wanted = new TreeSet<>(days);
        sub.getReminderSettings().removeIf(r -> !wanted.contains(r.getDaysBefore()));
        sub.getReminderSettings().forEach(r -> wanted.remove(r.getDaysBefore()));
        wanted.forEach(d -> sub.getReminderSettings().add(new ReminderSetting(sub, d)));
    }

    static SubscriptionResponse toResponse(Subscription s, LocalDate today) {
        List<Integer> days = s.getReminderSettings().stream()
                .map(ReminderSetting::getDaysBefore).sorted((a, b) -> b - a).toList();
        return new SubscriptionResponse(
                s.getId(), s.getName(), s.getCategory(), s.getAmount(),
                s.getBillingCycle().monthlyAmount(s.getAmount()), s.getBillingCycle(),
                s.getStartDate(), s.getNextDueDate(), ChronoUnit.DAYS.between(today, s.getNextDueDate()),
                s.getStatus(), s.isAutoRenew(), s.getNotes(), days, s.getCreatedAt(), s.getUpdatedAt());
    }
}
