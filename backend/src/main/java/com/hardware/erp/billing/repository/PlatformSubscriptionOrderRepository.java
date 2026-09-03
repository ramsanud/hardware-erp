package com.hardware.erp.billing.repository;

import com.hardware.erp.billing.entity.PlatformSubscriptionOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PlatformSubscriptionOrderRepository extends JpaRepository<PlatformSubscriptionOrder, Long> {

    Optional<PlatformSubscriptionOrder> findByRazorpayOrderId(String razorpayOrderId);

    Optional<PlatformSubscriptionOrder> findByIdAndTenantId(Long id, Long tenantId);

    Page<PlatformSubscriptionOrder> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    long countByStatusAndCreatedAtBetween(
            com.hardware.erp.billing.entity.SubscriptionOrderStatus status, LocalDateTime from, LocalDateTime to);
}
