package com.hardware.erp.deliverychallan.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Proof that goods left the shop without (yet) raising a GST tax invoice -
 * see V36's header comment for the full design. Not a financial record:
 * totalValuePaise is informational only, never a tax total.
 */
@Entity
@Table(name = "delivery_challan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryChallan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_challan_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "delivery_challan_number", nullable = false, length = 30)
    private String deliveryChallanNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "challan_date", nullable = false)
    private LocalDate challanDate;

    @Column(name = "transport_mode", length = 50)
    private String transportMode;

    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @Column(name = "delivery_address", length = 500)
    private String deliveryAddress;

    @Column(name = "total_value_paise", nullable = false)
    private Long totalValuePaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DeliveryChallanStatus status = DeliveryChallanStatus.ISSUED;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "source_sales_order_id")
    private Long sourceSalesOrderId;

    @Column(name = "converted_invoice_id")
    private Long convertedInvoiceId;

    @OneToMany(mappedBy = "deliveryChallan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DeliveryChallanItem> items = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;
}
