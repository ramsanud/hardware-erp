package com.hardware.erp.billing.controller;

import com.hardware.erp.billing.service.SubscriptionBillingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Inbound Razorpay webhook - authenticity enforced by the X-Razorpay-Signature
 * HMAC check inside SubscriptionBillingService, not by Spring Security (see
 * SecurityConfig's permitAll list, same pattern as /v1/webhooks/whatsapp).
 */
@Slf4j
@RestController
@RequestMapping("/v1/webhooks/razorpay")
@RequiredArgsConstructor
@Tag(name = "Billing")
public class RazorpayWebhookController {

    private final SubscriptionBillingService billingService;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader) {
        boolean accepted = billingService.handleWebhook(rawBody, signatureHeader);
        return accepted ? ResponseEntity.ok().build() : ResponseEntity.status(403).build();
    }
}
