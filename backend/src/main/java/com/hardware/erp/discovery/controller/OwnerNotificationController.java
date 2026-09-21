package com.hardware.erp.discovery.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.discovery.dto.DiscoveryDtos.OwnerNotificationResponse;
import com.hardware.erp.discovery.service.OwnerNotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** CR-090. Any signed-in user of the shop sees the shop's notifications - they are addressed to the shop, not to a person. */
@RestController
@RequestMapping("/v1/owner-notifications")
@RequiredArgsConstructor
@Tag(name = "Nearby Discovery")
public class OwnerNotificationController {

    private final OwnerNotificationService service;

    @GetMapping
    public ApiResponse<PageResponse<OwnerNotificationResponse>> search(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.search(unreadOnly, pageable));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("unread", service.unreadCount()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<OwnerNotificationResponse> markRead(@PathVariable Long id) {
        return ApiResponse.ok(service.markRead(id));
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead() {
        return ApiResponse.ok(Map.of("marked", service.markAllRead()));
    }
}
