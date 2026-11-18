package com.subtrack.repository;

import com.subtrack.entity.NotificationLog;
import com.subtrack.enums.NotificationStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /** Failed rows due for a retry, plus rows stuck in PENDING (crash between claim and send). */
    @Query("""
            select n.id from NotificationLog n
            where (n.status = :failed and n.nextRetryAt <= :now)
               or (n.status = :pending and n.createdAt < :staleBefore)
            order by n.id
            """)
    List<Long> findRetryableIds(@Param("failed") NotificationStatus failed,
                                @Param("pending") NotificationStatus pending,
                                @Param("now") Instant now,
                                @Param("staleBefore") Instant staleBefore);

    @Query(value = """
            select n from NotificationLog n
            join fetch n.billingPeriod p
            join fetch p.subscription s
            where s.user.id = :userId
            order by n.createdAt desc, n.id desc
            """,
            countQuery = """
            select count(n) from NotificationLog n
            where n.billingPeriod.subscription.user.id = :userId
            """)
    Page<NotificationLog> findForUser(@Param("userId") Long userId, Pageable pageable);
}
