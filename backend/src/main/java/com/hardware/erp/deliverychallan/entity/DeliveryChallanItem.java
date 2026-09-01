package com.hardware.erp.deliverychallan.entity;

import com.hardware.erp.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * No discount/GST ladder, unlike Invoice/Quotation/Sales Order items - a
 * challan is not a tax document. unitPricePaise/valuePaise are
 * informational snapshots at dispatch time only.
 */
@Entity
@Table(name = "delivery_challan_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryChallanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_challan_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_challan_id", nullable = false)
    private DeliveryChallan deliveryChallan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name_snapshot", nullable = false, length = 255)
    private String productNameSnapshot;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price_paise", nullable = false)
    private Long unitPricePaise;

    @Column(name = "value_paise", nullable = false)
    private Long valuePaise;
}
