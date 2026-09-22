package com.hardware.erp.discovery.dto;

import com.hardware.erp.discovery.entity.DiscoveryAvailability;
import com.hardware.erp.discovery.entity.OwnerNotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** CR-090. Grouped like SubstituteDtos - small records read together. */
public final class DiscoveryDtos {

    private DiscoveryDtos() {
    }

    @Schema(name = "DiscoverySettingRequest", description = "The shop's own consent to take part in nearby discovery")
    public record DiscoverySettingRequest(
            @NotNull Boolean discoveryEnabled,
            @NotNull Boolean shareShopName,
            @NotNull Boolean sharePhone,
            @NotNull Boolean shareApproximateLocation,
            @NotNull Boolean shareAvailability,
            @Schema(example = "9.925201")
            @DecimalMin(value = "-90") @DecimalMax(value = "90") BigDecimal latitude,
            @Schema(example = "78.119774")
            @DecimalMin(value = "-180") @DecimalMax(value = "180") BigDecimal longitude,
            @Schema(example = "5") @NotNull @Min(1) @Max(100) Integer searchRadiusKm
    ) {}

    @Schema(name = "DiscoverySettingResponse")
    public record DiscoverySettingResponse(
            boolean discoveryEnabled,
            boolean shareShopName,
            boolean sharePhone,
            boolean shareApproximateLocation,
            boolean shareAvailability,
            BigDecimal latitude,
            BigDecimal longitude,
            int searchRadiusKm,
            LocalDateTime updatedAt
    ) {}

    /**
     * One nearby shop. Every nullable field is null because THAT shop did
     * not consent to share it - never because the data was missing. No
     * source shop id is exposed.
     */
    @Schema(name = "NearbyShopResponse")
    public record NearbyShopResponse(
            Long matchId,
            @Schema(description = "Null unless the shop shares its name") String shopName,
            @Schema(description = "Null unless the shop shares its phone") String phone,
            @Schema(description = "wa.me link for the phone, null when there is no phone") String whatsappUrl,
            @Schema(description = "Null unless the shop shares its approximate location") BigDecimal distanceKm,
            String matchedProductName,
            DiscoveryAvailability availability,
            LocalDateTime foundAt
    ) {}

    @Schema(name = "NearbyAvailabilityResponse")
    public record NearbyAvailabilityResponse(
            Long productRequestId,
            String requestedProductName,
            @Schema(description = "True when this shop has not opted in or has no location, so it cannot search") boolean searchUnavailable,
            String searchUnavailableReason,
            List<NearbyShopResponse> shops
    ) {}

    @Schema(name = "OwnerNotificationResponse")
    public record OwnerNotificationResponse(
            Long id,
            OwnerNotificationType notificationType,
            String title,
            String body,
            String referenceType,
            Long referenceId,
            LocalDateTime readAt,
            LocalDateTime createdAt
    ) {}
}
