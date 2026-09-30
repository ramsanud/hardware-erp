package com.hardware.erp.product.substitute.entity;

import com.hardware.erp.product.entity.Product;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CR-089. What a customer asked for that the shop could not sell them
 * right then, plus what was suggested instead and what the owner chose.
 *
 * Named ProductRequestRecord, not ProductRequest: `ProductRequest` is
 * already the product-create/update DTO (product/dto/ProductRequest.java)
 * and the naming law forbids two different concepts sharing a name. The
 * table is `product_request`, which is the right name for the row.
 *
 * Customer identity is optional and deliberately minimal (a name and a
 * mobile the counter staff may type). It is never sent anywhere - CR-090's
 * nearby-shop contact flow explicitly does not forward it.
 */
@Entity
@Table(name = "product_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequestRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_product_id", nullable = false)
    private Product requestedProduct;

    @Column(name = "requested_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal requestedQuantity;

    /** Optional. What the customer said they were willing to spend, in paise - see SubstituteSetting.showAboveBudget. */
    @Column(name = "requested_budget_paise")
    private Long requestedBudgetPaise;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "customer_mobile", length = 15)
    private String customerMobile;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProductRequestStatus status = ProductRequestStatus.OPEN;

    /**
     * The alternative the owner actually chose to offer. Never set by the
     * engine - §24: the system only ever says "these may be suitable", the
     * decision is always the owner's, and choosing one here does NOT alter
     * any invoice; billing continues separately with whatever was really
     * sold.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_product_id")
    private Product selectedProduct;

    @Column(name = "selected_by")
    private Long selectedBy;

    @Column(name = "selected_at")
    private LocalDateTime selectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @OneToMany(mappedBy = "productRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("score DESC")
    @Builder.Default
    private List<ProductRequestSuggestion> suggestions = new ArrayList<>();
}
