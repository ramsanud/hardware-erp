package com.hardware.erp.platformadmin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Singleton row (id always 1, DB CHECK-constrained) - one Razorpay account
 * for the whole platform, filled in from the Platform Settings page rather
 * than requiring a redeploy with new RAZORPAY_* environment variables. See
 * RazorpayConfigResolver for how this and the env-var RazorpayProperties
 * combine (this row wins when present and enabled; env vars are the
 * fallback, never both applied at once).
 */
@Entity
@Table(name = "platform_razorpay_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformRazorpayConfig {

    @Id
    @Column(name = "platform_razorpay_config_id")
    @Builder.Default
    private Long id = 1L;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "key_id", length = 100)
    private String keyId;

    @Convert(converter = EncryptedSecretConverter.class)
    @Column(name = "key_secret_encrypted", length = 500)
    private String keySecret;

    @Convert(converter = EncryptedSecretConverter.class)
    @Column(name = "webhook_secret_encrypted", length = 500)
    private String webhookSecret;

    @Column(name = "pro_plan_amount_paise", nullable = false)
    @Builder.Default
    private Long proPlanAmountPaise = 99_900L;

    @Column(name = "max_plan_amount_paise", nullable = false)
    @Builder.Default
    private Long maxPlanAmountPaise = 299_900L;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    public boolean active() {
        return enabled && keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    public boolean webhookActive() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}
