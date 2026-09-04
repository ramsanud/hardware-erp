package com.hardware.erp.billing.repository;

import com.hardware.erp.billing.entity.PlatformSubscriptionPayment;
import com.hardware.erp.billing.entity.SubscriptionPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PlatformSubscriptionPaymentRepository extends JpaRepository<PlatformSubscriptionPayment, Long> {

    Optional<PlatformSubscriptionPayment> findByRazorpayPaymentId(String razorpayPaymentId);

    List<PlatformSubscriptionPayment> findByOrder_Tenant_IdOrderByCreatedAtDesc(Long tenantId);

    @Query("select p from PlatformSubscriptionPayment p where p.status = ?1 and p.createdAt between ?2 and ?3")
    List<PlatformSubscriptionPayment> findByStatusAndCreatedAtBetween(
            SubscriptionPaymentStatus status, LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(sum(p.amountPaise), 0) from PlatformSubscriptionPayment p "
            + "where p.status = com.hardware.erp.billing.entity.SubscriptionPaymentStatus.CAPTURED "
            + "and p.createdAt between ?1 and ?2")
    long sumCapturedAmountPaiseBetween(LocalDateTime from, LocalDateTime to);
}
