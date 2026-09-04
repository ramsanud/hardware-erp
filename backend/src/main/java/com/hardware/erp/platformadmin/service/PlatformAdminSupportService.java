package com.hardware.erp.platformadmin.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.platformadmin.dto.PlatformSupportDashboardResponse;
import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformAuditAction;
import com.hardware.erp.platformadmin.repository.PlatformAdminRepository;
import com.hardware.erp.supportticket.dto.SupportTicketDetailResponse;
import com.hardware.erp.supportticket.dto.SupportTicketSummaryResponse;
import com.hardware.erp.supportticket.dto.TicketMessageRequest;
import com.hardware.erp.supportticket.dto.TicketMessageResponse;
import com.hardware.erp.supportticket.entity.*;
import com.hardware.erp.supportticket.repository.SupportTicketMessageRepository;
import com.hardware.erp.supportticket.repository.SupportTicketRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Platform-admin side of the Support Center. Reuses SupportTicket/
 * SupportTicketMessage directly - no duplicate ticket model. Sees every
 * tenant's tickets, including internal notes (SupportTicketService's own
 * tenant-facing read path filters those out; this one deliberately does not).
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminSupportService {

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public PlatformSupportDashboardResponse dashboard(Long currentAdminId) {
        long open = ticketRepository.countByStatus(TicketStatus.OPEN);
        long inProgress = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
        long waiting = ticketRepository.countByStatus(TicketStatus.WAITING_FOR_USER);
        long high = ticketRepository.countByPriority(TicketPriority.HIGH);
        long urgent = ticketRepository.countByPriority(TicketPriority.URGENT);
        long assignedToMe = ticketRepository.countByAssignedAdminIdAndStatusNotIn(
                currentAdminId, List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED));
        long resolvedToday = ticketRepository.countResolvedSince(LocalDate.now().atStartOfDay());
        return new PlatformSupportDashboardResponse(open, inProgress, waiting, high + urgent, assignedToMe, resolvedToday);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketSummaryResponse> search(String search, TicketStatus status,
                                                               TicketPriority priority, TicketCategory category,
                                                               Long assignedAdminId, Pageable pageable) {
        return PageResponse.from(
                ticketRepository.search(search, status, priority, category, assignedAdminId, pageable),
                this::toSummary);
    }

    @Transactional(readOnly = true)
    public SupportTicketDetailResponse get(Long id) {
        SupportTicket ticket = require(id);
        var messages = messageRepository.findBySupportTicketIdOrderByCreatedAtAsc(id).stream()
                .map(this::toMessageResponse).toList();
        return toDetail(ticket, messages);
    }

    @Transactional
    public SupportTicketDetailResponse reply(Long id, TicketMessageRequest request, Long actingAdminId, HttpServletRequest httpRequest) {
        SupportTicket ticket = require(id);
        PlatformAdmin admin = platformAdminRepository.findById(actingAdminId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform admin", actingAdminId));

        messageRepository.save(SupportTicketMessage.builder()
                .supportTicketId(ticket.getId())
                .authorType(MessageAuthorType.PLATFORM_ADMIN)
                .authorId(actingAdminId)
                .authorName(admin.getFullName())
                .message(request.message().trim())
                .internal(request.internal())
                .build());

        if (!request.internal() && ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.WAITING_FOR_USER);
            ticketRepository.save(ticket);
        }

        audit(request.internal() ? PlatformAuditAction.SUPPORT_INTERNAL_NOTE_ADDED : PlatformAuditAction.SUPPORT_REPLIED,
                ticket, admin, httpRequest);
        return get(id);
    }

    @Transactional
    public SupportTicketSummaryResponse assign(Long id, Long assigneeAdminId, Long actingAdminId, HttpServletRequest request) {
        SupportTicket ticket = require(id);
        if (!platformAdminRepository.existsById(assigneeAdminId)) {
            throw new ResourceNotFoundException("Platform admin", assigneeAdminId);
        }
        ticket.setAssignedAdminId(assigneeAdminId);
        ticketRepository.save(ticket);
        audit(PlatformAuditAction.SUPPORT_ASSIGNED, ticket, actingAdminId, request);
        return toSummary(ticket);
    }

    @Transactional
    public SupportTicketSummaryResponse changePriority(Long id, TicketPriority priority, Long actingAdminId, HttpServletRequest request) {
        SupportTicket ticket = require(id);
        ticket.setPriority(priority);
        ticketRepository.save(ticket);
        audit(PlatformAuditAction.SUPPORT_PRIORITY_CHANGED, ticket, actingAdminId, request);
        return toSummary(ticket);
    }

    @Transactional
    public SupportTicketSummaryResponse changeStatus(Long id, TicketStatus status, Long actingAdminId, HttpServletRequest request) {
        SupportTicket ticket = require(id);
        if (ticket.getStatus() == status) {
            throw new BusinessException("Ticket is already " + status + ".");
        }
        ticket.setStatus(status);
        if (status == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
        } else {
            ticket.setResolvedAt(null);
        }
        ticketRepository.save(ticket);
        audit(PlatformAuditAction.SUPPORT_STATUS_CHANGED, ticket, actingAdminId, request);
        return toSummary(ticket);
    }

    private void audit(PlatformAuditAction action, SupportTicket ticket, Long actingAdminId, HttpServletRequest request) {
        PlatformAdmin admin = platformAdminRepository.getReferenceById(actingAdminId);
        audit(action, ticket, admin, request);
    }

    private void audit(PlatformAuditAction action, SupportTicket ticket, PlatformAdmin admin, HttpServletRequest request) {
        auditService.record(action, admin, true, "SUPPORT_TICKET", ticket.getId(), ticket.getSubject(), request);
    }

    private SupportTicket require(Long id) {
        return ticketRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Support ticket", id));
    }

    private SupportTicketSummaryResponse toSummary(SupportTicket ticket) {
        return new SupportTicketSummaryResponse(
                ticket.getId(), ticket.getTenant().getName(), ticket.getSubject(), ticket.getCategory(),
                ticket.getPriority(), ticket.getStatus(), ticket.getAssignedAdminId(),
                ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    private SupportTicketDetailResponse toDetail(SupportTicket ticket, List<TicketMessageResponse> messages) {
        return new SupportTicketDetailResponse(
                ticket.getId(), ticket.getTenant().getName(), ticket.getUser().getFullName(),
                ticket.getSubject(), ticket.getDescription(), ticket.getCategory(), ticket.getPriority(),
                ticket.getStatus(), ticket.getAssignedAdminId(), ticket.getCreatedAt(), ticket.getUpdatedAt(),
                ticket.getResolvedAt(), messages);
    }

    private TicketMessageResponse toMessageResponse(SupportTicketMessage message) {
        return new TicketMessageResponse(
                message.getId(), message.getAuthorType(), message.getAuthorName(),
                message.getMessage(), message.isInternal(), message.getCreatedAt());
    }
}
