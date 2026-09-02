package com.hardware.erp.supportticket.repository;

import com.hardware.erp.supportticket.entity.SupportTicket;
import com.hardware.erp.supportticket.entity.TicketCategory;
import com.hardware.erp.supportticket.entity.TicketPriority;
import com.hardware.erp.supportticket.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Optional<SupportTicket> findByIdAndTenantId(Long id, Long tenantId);

    Page<SupportTicket> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    /** Platform Admin - cross-tenant search, deliberately not tenant-scoped (see PlatformAdminSupportService). */
    @Query("""
           select t from SupportTicket t
           where (cast(:search as string) is null
                  or lower(t.subject) like lower(concat('%', cast(:search as string), '%'))
                  or lower(t.tenant.name) like lower(concat('%', cast(:search as string), '%')))
             and (:status is null or t.status = :status)
             and (:priority is null or t.priority = :priority)
             and (:category is null or t.category = :category)
             and (:assignedAdminId is null or t.assignedAdminId = :assignedAdminId)
           order by t.createdAt desc
           """)
    Page<SupportTicket> search(@Param("search") String search,
                                @Param("status") TicketStatus status,
                                @Param("priority") TicketPriority priority,
                                @Param("category") TicketCategory category,
                                @Param("assignedAdminId") Long assignedAdminId,
                                Pageable pageable);

    long countByStatus(TicketStatus status);

    long countByPriority(TicketPriority priority);

    long countByAssignedAdminIdAndStatusNotIn(Long assignedAdminId, java.util.List<TicketStatus> statuses);

    @Query("select count(t) from SupportTicket t where t.status = 'RESOLVED' and t.resolvedAt >= :since")
    long countResolvedSince(@Param("since") LocalDateTime since);
}
