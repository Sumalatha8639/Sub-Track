package com.subtrack.repository;

import com.subtrack.entity.BillingPeriodHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BillingPeriodHistoryRepository extends JpaRepository<BillingPeriodHistory, Long> {

    List<BillingPeriodHistory> findByBillingPeriodIdOrderByChangedAtDesc(Long billingPeriodId);
}
