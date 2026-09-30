package com.hardware.erp.inventory.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One row per (tenant, product): the current quantity on hand. Always
 * derived from stock_movement - never written to directly except by
 * StockServiceImpl applying a movement, so the cached total and the ledger
 * can never disagree.
 */
@Entity
@Table(name = "stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity_on_hand", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal quantityOnHand = BigDecimal.ZERO;

    /**
     * CR-091 Phase 6. Weighted-average cost per unit of what is on hand,
     * in paise. Moved only by StockServiceImpl on PURCHASE_RECEIPT (and its
     * reversal); a sale freezes it onto the invoice line and leaves it
     * unchanged. Backfilled from product.purchase_price_paise by V62.
     */
    @Column(name = "average_cost_paise", nullable = false)
    @Builder.Default
    private Long averageCostPaise = 0L;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;
}
