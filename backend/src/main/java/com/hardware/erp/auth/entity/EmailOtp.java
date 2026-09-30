package com.hardware.erp.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * CR-078 - a six-digit code sent by email. Stored hashed, short-lived,
 * attempt-limited, single-use. The raw code exists only in the email.
 *
 * {@code user} is null for a registration code: the account it will belong
 * to does not exist yet.
 */
@Entity
@Table(name = "email_otp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailOtp {

    /** Five wrong guesses kill the code. With a million possible values that leaves a 0.0005% chance per code. */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_otp_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Lower-cased by the service before it gets here, so equality lookups work without a functional index. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private EmailOtpPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isUsable() {
        return consumedAt == null && attempts < MAX_ATTEMPTS && expiresAt.isAfter(LocalDateTime.now());
    }

    /** A wrong guess. Returns true when this one was the last allowed. */
    public boolean registerFailedAttempt() {
        attempts++;
        return attempts >= MAX_ATTEMPTS;
    }

    public void consume() {
        this.consumedAt = LocalDateTime.now();
    }
}
