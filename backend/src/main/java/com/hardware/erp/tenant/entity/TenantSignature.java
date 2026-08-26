package com.hardware.erp.tenant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The drawn/uploaded signature image (CR-023). Distinct from
 * tenant.signatoryName (CR-022, a printed name) - when this image exists
 * it is used on the invoice/quotation PDF instead of the blank signature
 * line; signatoryName still prints underneath either way.
 */
@Entity
@Table(name = "tenant_signature")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSignature {

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
