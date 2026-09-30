package com.hardware.erp.discovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CR-090. One nearby shop that may have the requested product, snapshotted
 * with exactly the fields that shop permitted at the moment of the search.
 * shopName / phone / distanceKm are null when the source shop's flag was
 * off - the SQL that produced this row never selected them.
 */
@Entity
@Table(name = "product_request_discovery_match")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequestDiscoveryMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_request_discovery_match_id")
    private Long id;

    @Column(name = "product_request_id", nullable = false)
    private Long productRequestId;

    @Column(name = "source_tenant_id", nullable = false)
    private Long sourceTenantId;

    @Column(name = "matched_product_name", nullable = false, length = 255)
    private String matchedProductName;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 20)
    private DiscoveryAvailability availability;

    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "shop_name", length = 200)
    private String shopName;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
