package com.subtrack.repository;

import com.subtrack.entity.Subscription;
import com.subtrack.enums.SubscriptionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long>,
        JpaSpecificationExecutor<Subscription> {

    /** Ownership-safe lookup: another user's or a deleted subscription is simply "not found". */
    Optional<Subscription> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    /** All of a user's non-deleted subscriptions (dashboard). */
    List<Subscription> findByUserIdAndDeletedAtIsNull(Long userId);

    /** Auto-renew subscriptions that may be past due in some timezone (renewal job). */
    @Query("""
            select s.id from Subscription s
            where s.status = :status and s.autoRenew = true and s.deletedAt is null
              and s.nextDueDate < :cutoff
            order by s.id
            """)
    List<Long> findAutoRenewCandidateIds(@Param("status") SubscriptionStatus status,
                                         @Param("cutoff") LocalDate cutoff);
}
