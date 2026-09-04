package com.hardware.erp.supportticket.repository;

import com.hardware.erp.supportticket.entity.SupportTicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, Long> {

    List<SupportTicketMessage> findBySupportTicketIdOrderByCreatedAtAsc(Long supportTicketId);

    /** Tenant-facing thread - excludes admin-only internal notes. */
    List<SupportTicketMessage> findBySupportTicketIdAndInternalFalseOrderByCreatedAtAsc(Long supportTicketId);
}
