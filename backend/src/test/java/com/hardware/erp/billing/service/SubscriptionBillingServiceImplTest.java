package com.hardware.erp.billing.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.billing.config.EffectiveRazorpayConfig;
import com.hardware.erp.billing.dto.SubscriptionOrderResponse;
import com.hardware.erp.billing.dto.VerifyPaymentRequest;
import com.hardware.erp.billing.entity.PaymentSource;
import com.hardware.erp.billing.entity.PlatformSubscriptionOrder;
import com.hardware.erp.billing.entity.PlatformSubscriptionPayment;
import com.hardware.erp.billing.entity.SubscriptionOrderStatus;
import com.hardware.erp.billing.entity.SubscriptionPaymentStatus;
import com.hardware.erp.billing.repository.PlatformSubscriptionOrderRepository;
import com.hardware.erp.billing.repository.PlatformSubscriptionPaymentRepository;
import com.hardware.erp.billing.service.impl.SubscriptionBillingServiceImpl;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.config.DeploymentMode;
import com.hardware.erp.config.DeploymentProperties;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the fail-safe half (never a fake success when Razorpay isn't
 * configured), signature verification against a real computed HMAC (not a
 * live Razorpay call - that belongs behind a stub HTTP server, and
 * Testcontainers/Docker is the only place this project's IT suite runs),
 * and the idempotency guarantee webhook redelivery and the client /verify
 * call both rely on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionBillingServiceImplTest {

    private static final String KEY_ID = "rzp_test_key";
    private static final String KEY_SECRET = "test_key_secret";
    private static final String WEBHOOK_SECRET = "test_webhook_secret";

    @Mock private RazorpayConfigResolver configResolver;
    @Mock private RazorpayOrderClient razorpayOrderClient;
    @Mock private PlatformSubscriptionOrderRepository orderRepository;
    @Mock private PlatformSubscriptionPaymentRepository paymentRepository;
    @Mock private TenantRepository tenantRepository;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).subscriptionTier(SubscriptionTier.FREE).build();
        Role role = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE).permissions(new LinkedHashSet<>()).build();
        User authUser = User.builder().id(1L).tenant(tenant).role(role)
                .fullName("Owner").mobileNo("9999999999").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(authUser), null, List.of()));

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(PlatformSubscriptionOrder.class))).thenAnswer(i -> {
            PlatformSubscriptionOrder order = i.getArgument(0);
            if (order.getId() == null) order.setId(100L);
            return order;
        });
        when(paymentRepository.save(any(PlatformSubscriptionPayment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SubscriptionBillingServiceImpl serviceWith(EffectiveRazorpayConfig config) {
        return serviceWith(config, CLOUD_DEPLOYMENT);
    }

    private SubscriptionBillingServiceImpl serviceWith(EffectiveRazorpayConfig config, DeploymentProperties deployment) {
        when(configResolver.resolve()).thenReturn(config);
        return new SubscriptionBillingServiceImpl(
                deployment, configResolver, razorpayOrderClient, orderRepository, paymentRepository, tenantRepository);
    }

    /** CR-059 - the hosted deployment, where billing applies. What every pre-CR-059 test assumed. */
    private static final DeploymentProperties CLOUD_DEPLOYMENT =
            new DeploymentProperties(DeploymentMode.CLOUD, "", null);

    /** CR-059 - a client's own Docker install: nothing to subscribe to. */
    private static final DeploymentProperties SELF_HOSTED_DEPLOYMENT =
            new DeploymentProperties(DeploymentMode.SELF_HOSTED, "", null);

    private EffectiveRazorpayConfig activeConfig() {
        return new EffectiveRazorpayConfig(
                true, KEY_ID, KEY_SECRET, true, WEBHOOK_SECRET, "https://api.razorpay.com/v1", 99_900L, 299_900L);
    }

    private EffectiveRazorpayConfig inactiveConfig() {
        return new EffectiveRazorpayConfig(
                false, "", "", false, "", "https://api.razorpay.com/v1", 99_900L, 299_900L);
    }

    @Test
    @DisplayName("createOrder() refuses with an honest 503 when Razorpay is not configured, never a fake order")
    void createOrderFailsClosedWhenNotConfigured() {
        var service = serviceWith(inactiveConfig());

        assertThatThrownBy(() -> service.createOrder(SubscriptionTier.PRO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(be.getCode()).isEqualTo("BILLING_NOT_CONFIGURED");
                });
        verifyNoInteractions(razorpayOrderClient);
    }

    @Test
    @DisplayName("CR-059 - createOrder() refuses on a self-hosted install even with working Razorpay keys")
    void createOrderRefusedWhenSelfHosted() {
        // Deliberately an ACTIVE config: the point is that a self-hosted
        // install must refuse on deployment grounds, not merely because it
        // happens to have no credentials. A reseller who configures keys on a
        // self-hosted box must still not be able to charge the shop through
        // this endpoint unless they turn billing on explicitly.
        var service = serviceWith(activeConfig(), SELF_HOSTED_DEPLOYMENT);

        assertThatThrownBy(() -> service.createOrder(SubscriptionTier.PRO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(be.getCode()).isEqualTo("BILLING_NOT_APPLICABLE");
                });
        verifyNoInteractions(razorpayOrderClient);
    }

    @Test
    @DisplayName("CR-059 - a self-hosted install rejects the public Razorpay webhook before parsing it")
    void webhookRejectedWhenSelfHosted() {
        // /v1/webhooks/razorpay is permitAll by design, so on a self-hosted box
        // it is an internet-reachable path for a feature that install does not
        // have. A validly signed body must still not move anyone's tier.
        var service = serviceWith(activeConfig(), SELF_HOSTED_DEPLOYMENT);
        String body = "{\"event\":\"payment.captured\"}";

        assertThat(service.handleWebhook(body, hmac(body, WEBHOOK_SECRET))).isFalse();
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("CR-059 - a reseller can opt a self-hosted install back into billing")
    void selfHostedCanOptIntoBilling() {
        var service = serviceWith(activeConfig(),
                new DeploymentProperties(DeploymentMode.SELF_HOSTED, "", true));
        when(razorpayOrderClient.createOrder(eq(KEY_ID), eq(KEY_SECRET), any(), eq(99_900L), eq("INR"), any()))
                .thenReturn("order_optin");

        SubscriptionOrderResponse response = service.createOrder(SubscriptionTier.PRO);

        assertThat(response.razorpayOrderId()).isEqualTo("order_optin");
    }

    @Test
    @DisplayName("createOrder() rejects FREE - it is the default tier, never something to buy")
    void createOrderRejectsFreeTier() {
        var service = serviceWith(activeConfig());

        assertThatThrownBy(() -> service.createOrder(SubscriptionTier.FREE))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(razorpayOrderClient);
    }

    @Test
    @DisplayName("createOrder() calls Razorpay with the configured PRO amount and persists the order")
    void createOrderCreatesAndPersists() {
        var service = serviceWith(activeConfig());
        when(razorpayOrderClient.createOrder(eq(KEY_ID), eq(KEY_SECRET), any(), eq(99_900L), eq("INR"), any()))
                .thenReturn("order_ABC123");

        SubscriptionOrderResponse response = service.createOrder(SubscriptionTier.PRO);

        assertThat(response.razorpayOrderId()).isEqualTo("order_ABC123");
        assertThat(response.razorpayKeyId()).isEqualTo(KEY_ID);
        assertThat(response.amountPaise()).isEqualTo(99_900L);
        assertThat(response.status()).isEqualTo(SubscriptionOrderStatus.CREATED);
        verify(orderRepository).save(any(PlatformSubscriptionOrder.class));
    }

    @Test
    @DisplayName("verifyPayment() applies the tier upgrade only when the HMAC signature genuinely matches")
    void verifyPaymentAppliesUpgradeOnValidSignature() {
        var service = serviceWith(activeConfig());

        PlatformSubscriptionOrder order = PlatformSubscriptionOrder.builder()
                .id(100L).tenant(tenant).requestedTier(SubscriptionTier.PRO)
                .amountPaise(99_900L).currency("INR").razorpayOrderId("order_ABC123")
                .status(SubscriptionOrderStatus.CREATED).build();
        when(orderRepository.findByRazorpayOrderId("order_ABC123")).thenReturn(Optional.of(order));
        when(paymentRepository.findByRazorpayPaymentId("pay_XYZ")).thenReturn(Optional.empty());

        String signature = hmac("order_ABC123|pay_XYZ", KEY_SECRET);
        service.verifyPayment(new VerifyPaymentRequest("order_ABC123", "pay_XYZ", signature));

        assertThat(order.getStatus()).isEqualTo(SubscriptionOrderStatus.PAID);
        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.PRO);
        verify(paymentRepository).save(any(PlatformSubscriptionPayment.class));
    }

    @Test
    @DisplayName("verifyPayment() rejects a forged signature and never touches the tenant's tier")
    void verifyPaymentRejectsBadSignature() {
        var service = serviceWith(activeConfig());

        PlatformSubscriptionOrder order = PlatformSubscriptionOrder.builder()
                .id(100L).tenant(tenant).requestedTier(SubscriptionTier.PRO)
                .amountPaise(99_900L).currency("INR").razorpayOrderId("order_ABC123")
                .status(SubscriptionOrderStatus.CREATED).build();
        when(orderRepository.findByRazorpayOrderId("order_ABC123")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.verifyPayment(
                new VerifyPaymentRequest("order_ABC123", "pay_XYZ", "not-a-real-signature")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("PAYMENT_SIGNATURE_INVALID"));

        assertThat(order.getStatus()).isEqualTo(SubscriptionOrderStatus.CREATED);
        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.FREE);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleWebhook() rejects when no webhook secret is configured")
    void webhookRejectedWithoutSecret() {
        var service = serviceWith(new EffectiveRazorpayConfig(true, KEY_ID, KEY_SECRET, false, "", "https://api.razorpay.com/v1", 99_900L, 299_900L));

        boolean accepted = service.handleWebhook("{}", "sig");

        assertThat(accepted).isFalse();
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("handleWebhook() applies a payment.captured event with a valid signature, idempotently")
    void webhookAppliesCapturedPaymentIdempotently() {
        var service = serviceWith(activeConfig());

        PlatformSubscriptionOrder order = PlatformSubscriptionOrder.builder()
                .id(100L).tenant(tenant).requestedTier(SubscriptionTier.MAX)
                .amountPaise(299_900L).currency("INR").razorpayOrderId("order_ABC123")
                .status(SubscriptionOrderStatus.CREATED).build();
        when(orderRepository.findByRazorpayOrderId("order_ABC123")).thenReturn(Optional.of(order));
        when(paymentRepository.findByRazorpayPaymentId("pay_WEBHOOK1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(PlatformSubscriptionPayment.builder()
                        .id(1L).order(order).razorpayPaymentId("pay_WEBHOOK1").amountPaise(299_900L)
                        .status(SubscriptionPaymentStatus.CAPTURED).source(PaymentSource.WEBHOOK).build()));

        String body = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_WEBHOOK1","order_id":"order_ABC123"}}}}""";
        String signature = hmac(body, WEBHOOK_SECRET);

        boolean firstDelivery = service.handleWebhook(body, signature);
        boolean redelivery = service.handleWebhook(body, signature);

        assertThat(firstDelivery).isTrue();
        assertThat(redelivery).isTrue();
        assertThat(tenant.getSubscriptionTier()).isEqualTo(SubscriptionTier.MAX);
        // Only the first delivery actually inserted a payment row - the redelivery found it already there.
        verify(paymentRepository, times(1)).save(any(PlatformSubscriptionPayment.class));
    }

    @Test
    @DisplayName("handleWebhook() rejects a body whose signature does not match the webhook secret")
    void webhookRejectsForgedSignature() {
        var service = serviceWith(activeConfig());

        boolean accepted = service.handleWebhook("{\"event\":\"payment.captured\"}", "forged");

        assertThat(accepted).isFalse();
        verifyNoInteractions(orderRepository);
    }

    private static String hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
