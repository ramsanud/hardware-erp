package com.hardware.erp.supportticket.service;

import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.supportticket.dto.*;
import com.hardware.erp.supportticket.entity.MessageAuthorType;
import com.hardware.erp.supportticket.entity.SupportTicket;
import com.hardware.erp.supportticket.entity.SupportTicketMessage;
import com.hardware.erp.supportticket.repository.SupportTicketMessageRepository;
import com.hardware.erp.supportticket.repository.SupportTicketRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-facing side: any signed-in tenant user can raise/view/reply to a
 * ticket for their own shop - not gated by a permission code, matching
 * NotificationService.contactAdmin()'s own "any signed-in user" precedent
 * (CR-028). Scoped to the whole tenant, not just the raising user - an
 * OWNER should see tickets any of their own staff raised, the same
 * boundary every other tenant-owned resource in this app uses.
 */
@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @Transactional
    public SupportTicketSummaryResponse create(CreateTicketRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        AppUserDetails caller = SecurityUtils.requireCurrentUser();
        var tenant = tenantRepository.getReferenceById(tenantId);
        var user = userRepository.getReferenceById(caller.getId());

        SupportTicket ticket = ticketRepository.save(SupportTicket.builder()
                .tenant(tenant)
                .user(user)
                .subject(request.subject().trim())
                .description(request.description().trim())
                .category(request.category())
                .build());
        return toSummary(ticket);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketSummaryResponse> listMine(Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                ticketRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable),
                this::toSummary);
    }

    @Transactional(readOnly = true)
    public SupportTicketDetailResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SupportTicket ticket = require(id, tenantId);
        var messages = messageRepository.findBySupportTicketIdAndInternalFalseOrderByCreatedAtAsc(id).stream()
                .map(this::toMessageResponse).toList();
        return toDetail(ticket, messages);
    }

    @Transactional
    public SupportTicketDetailResponse reply(Long id, TicketMessageRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        AppUserDetails caller = SecurityUtils.requireCurrentUser();
        SupportTicket ticket = require(id, tenantId);

        messageRepository.save(SupportTicketMessage.builder()
                .supportTicketId(ticket.getId())
                .authorType(MessageAuthorType.TENANT_USER)
                .authorId(caller.getId())
                .authorName(caller.getFullName())
                .message(request.message().trim())
                .internal(false)
                .build());

        // A tenant reply to a resolved/closed ticket reopens it - the shop is
        // saying the problem is not actually done, not asking to file a new one.
        if (ticket.getStatus() == com.hardware.erp.supportticket.entity.TicketStatus.RESOLVED
                || ticket.getStatus() == com.hardware.erp.supportticket.entity.TicketStatus.CLOSED) {
            ticket.setStatus(com.hardware.erp.supportticket.entity.TicketStatus.OPEN);
        } else if (ticket.getStatus() == com.hardware.erp.supportticket.entity.TicketStatus.WAITING_FOR_USER) {
            ticket.setStatus(com.hardware.erp.supportticket.entity.TicketStatus.IN_PROGRESS);
        }
        ticketRepository.save(ticket);

        return get(id);
    }

    private SupportTicket require(Long id, Long tenantId) {
        return ticketRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket", id));
    }

    private SupportTicketSummaryResponse toSummary(SupportTicket ticket) {
        return new SupportTicketSummaryResponse(
                ticket.getId(), null, ticket.getSubject(), ticket.getCategory(), ticket.getPriority(),
                ticket.getStatus(), ticket.getAssignedAdminId(), ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    private SupportTicketDetailResponse toDetail(SupportTicket ticket, java.util.List<TicketMessageResponse> messages) {
        User raisedBy = ticket.getUser();
        return new SupportTicketDetailResponse(
                ticket.getId(), ticket.getTenant().getName(), raisedBy.getFullName(),
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
