package com.hardware.erp.branch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CR-092. A measured per-branch breakdown of `stock.quantity_on_hand`,
 * written by the same code that writes the tenant row
 * (StockServiceImpl), so the branches of a shop always sum to the shop.
 * Never the availability authority - that stays `stock`. Written only
 * through {@link com.hardware.erp.branch.repository.BranchStockRepository#addQuantity}.
 */
@Entity
@Table(name = "branch_stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_stock_id")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity_on_hand", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantityOnHand;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
