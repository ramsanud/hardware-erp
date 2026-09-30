package com.hardware.erp.discovery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * CR-090. One shop's consent to take part in nearby discovery. Every
 * flag is false until the owner turns it on, and the coordinates live
 * here rather than on Tenant so a shop that never opted in has no stored
 * location at all. The database CHECK forbids discovery_enabled without a
 * location.
 */
@Entity
@Table(name = "shop_discovery_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopDiscoverySetting {

    public static final int DEFAULT_RADIUS_KM = 5;

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "discovery_enabled", nullable = false)
    private boolean discoveryEnabled;

    @Column(name = "share_shop_name", nullable = false)
    private boolean shareShopName;

    @Column(name = "share_phone", nullable = false)
    private boolean sharePhone;

    @Column(name = "share_approximate_location", nullable = false)
    private boolean shareApproximateLocation;

    @Column(name = "share_availability", nullable = false)
    private boolean shareAvailability;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "search_radius_km", nullable = false)
    @Builder.Default
    private Integer searchRadiusKm = DEFAULT_RADIUS_KM;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    /** Everything off, no location, default radius - what a shop that has never opened the card gets. */
    public static ShopDiscoverySetting defaults(Long tenantId) {
        return ShopDiscoverySetting.builder().tenantId(tenantId).createdAt(LocalDateTime.now()).build();
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
