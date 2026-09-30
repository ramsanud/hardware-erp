package com.hardware.erp.subscription.repository;

import com.hardware.erp.subscription.entity.SubscriptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {

    List<SubscriptionHistory> findTop20ByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
