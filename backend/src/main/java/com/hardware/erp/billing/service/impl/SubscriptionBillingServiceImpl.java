package com.hardware.erp.billing.service.impl;

import com.hardware.erp.billing.config.EffectiveRazorpayConfig;
import com.hardware.erp.billing.dto.SubscriptionOrderResponse;
import com.hardware.erp.billing.dto.SubscriptionPaymentResponse;
import com.hardware.erp.billing.dto.TenantBillingHistoryResponse;
import com.hardware.erp.billing.dto.VerifyPaymentRequest;
import com.hardware.erp.billing.entity.*;
import com.hardware.erp.billing.repository.PlatformSubscriptionOrderRepository;
import com.hardware.erp.billing.repository.PlatformSubscriptionPaymentRepository;
import com.hardware.erp.billing.service.RazorpayConfigResolver;
import com.hardware.erp.billing.service.RazorpayOrderClient;
import com.hardware.erp.billing.service.SubscriptionBillingService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.config.DeploymentProperties;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBillingServiceImpl implements SubscriptionBillingService {

    private final DeploymentProperties deployment;
    private final RazorpayConfigResolver configResolver;
    private final RazorpayOrderClient razorpayOrderClient;
    private final PlatformSubscriptionOrderRepository orderRepository;
    private final PlatformSubscriptionPaymentRepository paymentRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public SubscriptionOrderResponse createOrder(SubscriptionTier requestedTier) {
        requireBillingApplicable();
        EffectiveRazorpayConfig config = configResolver.resolve();
        if (!config.active()) {
            throw new BusinessException(
                    "Billing is not configured for this environment yet.",
                    HttpStatus.SERVICE_UNAVAILABLE, "BILLING_NOT_CONFIGURED");
        }
        if (requestedTier == SubscriptionTier.FREE) {
            throw new BusinessException("FREE is the default tier and cannot be purchased.");
        }

        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant not found.", HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        long amountPaise = requestedTier == SubscriptionTier.MAX
                ? config.maxPlanAmountPaise()
                : config.proPlanAmountPaise();

        String receipt = "tenant-" + tenantId + "-" + UUID.randomUUID();
        String razorpayOrderId = razorpayOrderClient.createOrder(
                config.keyId(), config.keySecret(), config.apiBaseUrl(), amountPaise, "INR", receipt);

        PlatformSubscriptionOrder order = orderRepository.save(PlatformSubscriptionOrder.builder()
                .tenant(tenant)
                .requestedTier(requestedTier)
                .amountPaise(amountPaise)
                .currency("INR")
                .razorpayOrderId(razorpayOrderId)
                .status(SubscriptionOrderStatus.CREATED)
                .build());

        return new SubscriptionOrderResponse(
                order.getId(), razorpayOrderId, config.keyId(),
                requestedTier, amountPaise, "INR", order.getStatus());
    }

    @Override
    @Transactional
    public void verifyPayment(VerifyPaymentRequest request) {
        requireBillingApplicable();
        EffectiveRazorpayConfig config = configResolver.resolve();
        if (!config.active()) {
            throw new BusinessException(
                    "Billing is not configured for this environment yet.",
                    HttpStatus.SERVICE_UNAVAILABLE, "BILLING_NOT_CONFIGURED");
        }

        PlatformSubscriptionOrder order = orderRepository.findByRazorpayOrderId(request.razorpayOrderId())
                .orElseThrow(() -> new BusinessException("Unknown order.", HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND"));

        // Only the tenant that created this order may verify a payment against it.
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        if (!order.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Order not found.", HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND");
        }

        boolean signatureValid = verifyPaymentSignature(
                request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature(), config.keySecret());
        if (!signatureValid) {
            log.warn("Razorpay payment signature did not match for order {}", request.razorpayOrderId());
            throw new BusinessException(
                    "Payment could not be verified. If money was deducted, it will be reconciled automatically.",
                    HttpStatus.BAD_REQUEST, "PAYMENT_SIGNATURE_INVALID");
        }

        applyCapturedPayment(order, request.razorpayPaymentId(), request.razorpaySignature(), PaymentSource.CLIENT_VERIFY);
    }

    @Override
    @Transactional
    public boolean handleWebhook(String rawBody, String signatureHeader) {
        // CR-059. The webhook endpoint is public by design (Razorpay carries no
        // JWT of ours), so on a self-hosted install it is an internet-facing
        // path for a feature that install does not have. Refuse before parsing
        // anything, rather than relying on there being no order to match.
        if (!deployment.billingApplies()) {
            log.warn("Razorpay webhook received on a deployment where billing does not apply - rejecting");
            return false;
        }
        EffectiveRazorpayConfig config = configResolver.resolve();
        if (!config.webhookActive()) {
            log.warn("Razorpay webhook received but no webhook secret configured - rejecting");
            return false;
        }
        if (!webhookSignatureValid(rawBody, signatureHeader, config.webhookSecret())) {
            log.warn("Rejected a Razorpay webhook - signature missing or did not match");
            return false;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.warn("Could not parse Razorpay webhook body", e);
            return true; // acked - malformed body is not a signature problem, do not make Razorpay retry forever
        }

        String event = root.path("event").asText("");
        if (!"payment.captured".equals(event) && !"order.paid".equals(event)) {
            return true; // ack, nothing to do for events this module does not model
        }

        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String razorpayPaymentId = paymentEntity.path("id").asText(null);
        String razorpayOrderId = paymentEntity.path("order_id").asText(null);
        if (razorpayPaymentId == null || razorpayOrderId == null) {
            return true;
        }

        orderRepository.findByRazorpayOrderId(razorpayOrderId).ifPresentOrElse(
                order -> applyCapturedPayment(order, razorpayPaymentId, null, PaymentSource.WEBHOOK),
                () -> log.warn("Razorpay webhook for an unknown order id {} - dropped", razorpayOrderId));
        return true;
    }

    /**
     * Idempotent: razorpay_payment_id is UNIQUE, so a redelivered webhook or
     * a webhook racing the client-side /verify call for the same payment
     * simply finds the row already there and returns without reapplying the
     * tier change a second time.
     */
    private void applyCapturedPayment(PlatformSubscriptionOrder order, String razorpayPaymentId,
                                       String signature, PaymentSource source) {
        if (paymentRepository.findByRazorpayPaymentId(razorpayPaymentId).isPresent()) {
            log.info("Razorpay payment {} already recorded - ignoring duplicate ({})", razorpayPaymentId, source);
            return;
        }

        paymentRepository.save(PlatformSubscriptionPayment.builder()
                .order(order)
                .razorpayPaymentId(razorpayPaymentId)
                .razorpaySignature(signature)
                .amountPaise(order.getAmountPaise())
                .status(SubscriptionPaymentStatus.CAPTURED)
                .source(source)
                .capturedAt(LocalDateTime.now())
                .build());

        order.setStatus(SubscriptionOrderStatus.PAID);
        orderRepository.save(order);

        Tenant tenant = order.getTenant();
        if (tenant.getSubscriptionTier().ordinal() < order.getRequestedTier().ordinal()) {
            tenant.setSubscriptionTier(order.getRequestedTier());
            tenant.setSubscriptionTrialExpiresAt(null); // a paid tier is not a trial
            tenantRepository.save(tenant);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TenantBillingHistoryResponse history() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant not found.", HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));

        List<SubscriptionPaymentResponse> payments = paymentRepository.findByOrder_Tenant_IdOrderByCreatedAtDesc(tenantId)
                .stream().map(this::toResponse).toList();

        return new TenantBillingHistoryResponse(tenant.getSubscriptionTier(), payments);
    }

    private SubscriptionPaymentResponse toResponse(PlatformSubscriptionPayment payment) {
        return new SubscriptionPaymentResponse(
                payment.getId(), payment.getOrder().getId(), payment.getOrder().getRequestedTier(),
                payment.getAmountPaise(), payment.getOrder().getCurrency(),
                payment.getStatus(), payment.getSource(), payment.getCapturedAt());
    }

    /** Razorpay Checkout's own documented contract: HMAC-SHA256(order_id + "|" + payment_id, key_secret). */
    /**
     * CR-059. A self-hosted install is software the client has already bought;
     * charging them a monthly subscription for it is simply wrong, so checkout
     * is refused rather than merely hidden in the UI. Distinct from
     * BILLING_NOT_CONFIGURED, which means "this deployment does bill, but
     * Razorpay keys are missing" - an operator can fix that one.
     *
     * A reseller running self-hosted instances for several shops can turn this
     * back on with app.deployment.billing-enabled=true.
     */
    private void requireBillingApplicable() {
        if (!deployment.billingApplies()) {
            throw new BusinessException(
                    "This is a self-hosted installation. Subscription billing does not apply here - "
                            + "contact your supplier about your licence.",
                    HttpStatus.SERVICE_UNAVAILABLE, "BILLING_NOT_APPLICABLE");
        }
    }

    private boolean verifyPaymentSignature(String orderId, String paymentId, String signature, String keySecret) {
        return hmacMatches(orderId + "|" + paymentId, keySecret, signature);
    }

    /** Razorpay's webhook contract: HMAC-SHA256(raw request body, webhook secret). */
    private boolean webhookSignatureValid(String rawBody, String signatureHeader, String webhookSecret) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        return hmacMatches(rawBody, webhookSecret, signatureHeader);
    }

    private boolean hmacMatches(String payload, String secret, String expectedHex) {
        if (expectedHex == null || expectedHex.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8), expectedHex.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Could not compute HMAC for Razorpay signature check", e);
            return false;
        }
    }
}
