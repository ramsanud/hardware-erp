package com.hardware.erp.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** An uploaded UPI/GPay QR code image, printed in place of the auto-generated one (CR-026). See UserAvatar for why this is its own table. */
@Entity
@Table(name = "tenant_upi_qr")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantUpiQr {

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Integer fileSize;

    @Column(name = "image_data", nullable = false)
    private byte[] imageData;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
