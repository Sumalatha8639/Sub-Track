package com.subtrack.repository;

import com.subtrack.entity.BillingPeriod;
import com.subtrack.enums.PaymentStatus;
import com.subtrack.enums.SubscriptionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPeriodRepository extends JpaRepository<BillingPeriod, Long> {

    Optional<BillingPeriod> findBySubscriptionIdAndDueDate(Long subscriptionId, LocalDate dueDate);

    List<BillingPeriod> findBySubscriptionIdOrderByDueDateDesc(Long subscriptionId);

    /** Every period of a user's non-deleted subscriptions (dashboard). */
    @Query("""
            select p from BillingPeriod p
            join fetch p.subscription s
            where s.user.id = :userId and s.deletedAt is null
            """)
    List<BillingPeriod> findAllForUser(@Param("userId") Long userId);

    /** PENDING periods of a user's active subscriptions with due dates in [from, to] (reminder job). */
    @Query("""
            select p from BillingPeriod p
            join fetch p.subscription s
            where s.user.id = :userId
              and s.status = :active and s.deletedAt is null
              and p.paymentStatus = :pending
              and p.dueDate between :from and :to
            """)
    List<BillingPeriod> findPendingForReminders(@Param("userId") Long userId,
                                                @Param("active") SubscriptionStatus active,
                                                @Param("pending") PaymentStatus pending,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to);
}
