package com.hardware.erp.supportticket.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.supportticket.dto.*;
import com.hardware.erp.supportticket.service.SupportTicketService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Any signed-in tenant user - not permission-gated, matching
 * NotificationController's own contactAdmin precedent (CR-028).
 */
@RestController
@RequestMapping("/v1/support-tickets")
@RequiredArgsConstructor
@Tag(name = "Support Tickets")
public class SupportTicketController {

    private final SupportTicketService ticketService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SupportTicketSummaryResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        return ApiResponse.ok(ticketService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<SupportTicketSummaryResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(ticketService.listMine(pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<SupportTicketDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(ticketService.get(id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<SupportTicketDetailResponse> reply(
            @PathVariable Long id, @Valid @RequestBody TicketMessageRequest request) {
        return ApiResponse.ok(ticketService.reply(id, request));
    }
}
