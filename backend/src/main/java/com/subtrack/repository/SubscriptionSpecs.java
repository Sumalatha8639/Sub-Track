package com.subtrack.repository;

import com.subtrack.entity.Subscription;
import com.subtrack.enums.Category;
import com.subtrack.enums.SubscriptionStatus;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Composable filters for the subscription list. A null return means "no filter". */
public final class SubscriptionSpecs {

    private static final char ESCAPE = '!';

    private SubscriptionSpecs() {
    }

    public static Specification<Subscription> ownedBy(Long userId) {
        return (root, q, cb) -> cb.and(
                cb.equal(root.get("user").get("id"), userId),
                cb.isNull(root.get("deletedAt")));
    }

    public static Specification<Subscription> category(Category category) {
        return category == null ? null : (root, q, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Subscription> status(SubscriptionStatus status) {
        return status == null ? null : (root, q, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Subscription> dueOnOrBefore(LocalDate date) {
        return date == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("nextDueDate"), date);
    }

    /** Case-insensitive "name contains"; % and _ typed by the user are matched literally. */
    public static Specification<Subscription> nameContains(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        String pattern = "%" + escaped + "%";
        return (root, q, cb) -> cb.like(cb.lower(root.get("name")), pattern, ESCAPE);
    }
}
