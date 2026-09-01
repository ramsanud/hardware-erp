package com.hardware.erp.salesorder.mapper;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.salesorder.dto.SalesOrderItemResponse;
import com.hardware.erp.salesorder.dto.SalesOrderResponse;
import com.hardware.erp.salesorder.dto.SalesOrderSummaryResponse;
import com.hardware.erp.salesorder.entity.SalesOrder;
import com.hardware.erp.salesorder.entity.SalesOrderItem;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/** Mirrors QuotationMapper - see its header comments for the reasoning behind each derivation. */
@Component
public class SalesOrderMapper {

    public SalesOrderResponse toResponse(SalesOrder order) {
        return new SalesOrderResponse(
                order.getId(),
                order.getSalesOrderNumber(),
                order.getCustomer().getId(),
                order.getCustomer().getCustomerName(),
                order.getCustomer().getMobileNo(),
                order.getOrderDate(),
                order.getExpectedDeliveryDate(),
                rupees(grossSubtotal(order)),
                nullIfZero(productDiscount(order)),
                rupees(grossSubtotal(order) - productDiscount(order)),
                order.getDiscountType(),
                order.getDiscountPercent().toPlainString(),
                nullIfZero(order.getDiscountPaise()),
                nullIfZero(productDiscount(order) + zeroIfNull(order.getDiscountPaise())),
                rupees(order.getSubtotalPaise()),
                rupees(order.getGstAmountPaise()),
                rupees(order.getTotalPaise()),
                order.getStatus(),
                order.getRemarks(),
                order.getConvertedDeliveryChallanId(),
                order.getConvertedInvoiceId(),
                order.getItems().stream()
                        .sorted(Comparator.comparing(SalesOrderItem::getId))
                        .map(this::toResponse)
                        .toList(),
                order.getCreatedAt());
    }

    public SalesOrderSummaryResponse toSummary(SalesOrder order) {
        return new SalesOrderSummaryResponse(
                order.getId(),
                order.getSalesOrderNumber(),
                order.getCustomer().getCustomerName(),
                order.getCustomer().getMobileNo(),
                order.getOrderDate(),
                order.getExpectedDeliveryDate(),
                rupees(order.getTotalPaise()),
                order.getStatus());
    }

    public SalesOrderItemResponse toResponse(SalesOrderItem item) {
        return new SalesOrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                item.getQuantity(),
                rupees(item.getUnitPricePaise()),
                item.getGstRatePercent().toPlainString(),
                rupees(item.getLineSubtotalPaise()),
                rupees(item.getLineGstPaise()),
                rupees(item.getLineTotalPaise()),
                item.getDiscountType(),
                item.getDiscountPercent().toPlainString(),
                rupees(item.getDiscountAmountPaise()),
                rupees(lineGross(item)));
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    private static long productDiscount(SalesOrder order) {
        return order.getItems().stream()
                .mapToLong(item -> zeroIfNull(item.getDiscountAmountPaise()))
                .sum();
    }

    private static long grossSubtotal(SalesOrder order) {
        return order.getSubtotalPaise()
                + zeroIfNull(order.getDiscountPaise())
                + productDiscount(order);
    }

    private String nullIfZero(long paise) {
        return paise > 0 ? rupees(paise) : null;
    }

    private static long lineGross(SalesOrderItem item) {
        return java.math.BigDecimal.valueOf(item.getUnitPricePaise())
                .multiply(item.getQuantity())
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
    }

    private String rupees(Long paise) {
        return IndianCurrencyFormat.rupees(paise);
    }
}
