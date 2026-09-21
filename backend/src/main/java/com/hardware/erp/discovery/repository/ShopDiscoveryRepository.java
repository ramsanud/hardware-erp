package com.hardware.erp.discovery.repository;

import com.hardware.erp.discovery.entity.DiscoveryAvailability;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * CR-090. The one deliberately cross-tenant read in the system, and the
 * reason it is a hand-written native query rather than JPA: the consent
 * rules are enforced in the SELECT list itself.
 *
 *  - Only shops with discovery_enabled AND share_availability are rows.
 *  - shop name, phone and distance are each `CASE WHEN <that shop's flag>
 *    THEN value ELSE NULL END` - a shop that did not consent to a field
 *    never has it read, not "read then hidden".
 *  - Nothing else is selected. No price, no quantity (only the
 *    AVAILABLE / LIKELY_AVAILABLE bucket), no supplier, no other product.
 *  - Never the requesting shop itself. One row per source shop (the
 *    best-matching product), so the requester cannot enumerate a
 *    catalogue by asking for the same thing repeatedly.
 *
 * Product identity is matched on code / model / manufacturer code
 * (exact, case-insensitive) or a pg_trgm name similarity above the
 * threshold - the brief's "reliable product identity rather than only
 * the product name", with the name as a fallback for typos.
 *
 * Distance is the great-circle formula in SQL; the acos argument is
 * clamped to [-1, 1] because floating-point noise on two identical
 * points would otherwise throw. Radius filtering happens on that same
 * expression, so a shop 5.1 km away is not in a 5 km search.
 */
@Repository
@RequiredArgsConstructor
public class ShopDiscoveryRepository {

    /** Below this, two names are not the same product. Tuned for hardware names ("towr bolt 4 in" vs "Tower Bolt 4 Inch"). */
    static final double NAME_SIMILARITY_THRESHOLD = 0.45;

    private final JdbcTemplate jdbc;

    public record NearbyMatch(
            Long sourceTenantId,
            String matchedProductName,
            DiscoveryAvailability availability,
            /** Null when the source shop does not share its approximate location. */
            BigDecimal distanceKm,
            /** Null when the source shop does not share its name. */
            String shopName,
            /** Null when the source shop does not share its phone. */
            String phone
    ) {}

    private static final String SQL = """
            WITH requester AS (
                SELECT latitude, longitude, search_radius_km
                FROM shop_discovery_setting
                WHERE tenant_id = ?
            ),
            candidates AS (
                SELECT p.tenant_id,
                       p.product_name,
                       COALESCE(s.quantity_on_hand, 0) AS on_hand,
                       (6371 * acos(LEAST(1.0, GREATEST(-1.0,
                            cos(radians(r.latitude)) * cos(radians(d.latitude))
                              * cos(radians(d.longitude) - radians(r.longitude))
                            + sin(radians(r.latitude)) * sin(radians(d.latitude)))))) AS distance_km,
                       d.share_shop_name,
                       d.share_phone,
                       d.share_approximate_location,
                       CASE
                           WHEN lower(p.product_code) = lower(?) THEN 3
                           WHEN CAST(? AS VARCHAR) IS NOT NULL AND lower(coalesce(p.model_no, '')) = lower(CAST(? AS VARCHAR)) THEN 2
                           WHEN CAST(? AS VARCHAR) IS NOT NULL AND lower(coalesce(p.manufacturer_code, '')) = lower(CAST(? AS VARCHAR)) THEN 2
                           ELSE 1
                       END AS identity_rank,
                       similarity(p.product_name, ?) AS name_similarity
                FROM shop_discovery_setting d
                CROSS JOIN requester r
                JOIN tenant t ON t.tenant_id = d.tenant_id AND t.status = 'ACTIVE'
                JOIN product p ON p.tenant_id = d.tenant_id
                              AND p.deleted_at IS NULL
                              AND p.status = 'ACTIVE'
                LEFT JOIN stock s ON s.tenant_id = p.tenant_id AND s.product_id = p.product_id
                WHERE d.discovery_enabled = TRUE
                  AND d.share_availability = TRUE
                  AND d.tenant_id <> ?
                  AND d.latitude IS NOT NULL AND d.longitude IS NOT NULL
                  AND COALESCE(s.quantity_on_hand, 0) > 0
                  AND (
                        lower(p.product_code) = lower(?)
                     OR (CAST(? AS VARCHAR) IS NOT NULL AND lower(coalesce(p.model_no, '')) = lower(CAST(? AS VARCHAR)))
                     OR (CAST(? AS VARCHAR) IS NOT NULL AND lower(coalesce(p.manufacturer_code, '')) = lower(CAST(? AS VARCHAR)))
                     OR similarity(p.product_name, ?) > ?
                  )
            ),
            ranked AS (
                SELECT c.*,
                       row_number() OVER (PARTITION BY c.tenant_id
                                          ORDER BY c.identity_rank DESC, c.name_similarity DESC, c.on_hand DESC) AS rn
                FROM candidates c
                CROSS JOIN requester r
                WHERE c.distance_km <= r.search_radius_km
            )
            SELECT tenant_id,
                   product_name,
                   CASE WHEN on_hand >= ? THEN 'AVAILABLE' ELSE 'LIKELY_AVAILABLE' END AS availability,
                   CASE WHEN share_approximate_location THEN round(distance_km::numeric, 2) ELSE NULL END AS distance_km,
                   CASE WHEN share_shop_name THEN (SELECT name FROM tenant WHERE tenant.tenant_id = ranked.tenant_id) ELSE NULL END AS shop_name,
                   CASE WHEN share_phone THEN (SELECT phone FROM tenant WHERE tenant.tenant_id = ranked.tenant_id) ELSE NULL END AS phone
            FROM ranked
            WHERE rn = 1
            ORDER BY distance_km ASC NULLS LAST, availability ASC
            LIMIT ?
            """;

    /**
     * @param requestingTenantId  the shop searching - must itself have a location and be opted in (checked by the service)
     * @param productCode         the requested product's code
     * @param modelNo             nullable
     * @param manufacturerCode    nullable
     * @param productName         for the trigram fallback
     * @param requestedQuantity   drives AVAILABLE vs LIKELY_AVAILABLE
     * @param maxResults          hard cap on rows returned
     */
    public List<NearbyMatch> findNearby(Long requestingTenantId, String productCode, String modelNo,
                                        String manufacturerCode, String productName,
                                        BigDecimal requestedQuantity, int maxResults) {
        return jdbc.query(SQL, (rs, rowNum) -> new NearbyMatch(
                        rs.getLong("tenant_id"),
                        rs.getString("product_name"),
                        DiscoveryAvailability.valueOf(rs.getString("availability")),
                        rs.getBigDecimal("distance_km"),
                        rs.getString("shop_name"),
                        rs.getString("phone")),
                requestingTenantId,
                productCode, modelNo, modelNo, manufacturerCode, manufacturerCode, productName,
                requestingTenantId,
                productCode, modelNo, modelNo, manufacturerCode, manufacturerCode, productName,
                NAME_SIMILARITY_THRESHOLD,
                requestedQuantity,
                maxResults);
    }
}
