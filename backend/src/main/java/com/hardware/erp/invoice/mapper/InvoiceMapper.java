package com.hardware.erp.invoice.mapper;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.invoice.dto.*;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.InvoiceItem;
import com.hardware.erp.invoice.entity.Payment;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class InvoiceMapper {

    public InvoiceResponse toResponse(Invoice invoice, List<Payment> payments) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCustomer().getId(),
                invoice.getCustomer().getCustomerName(),
                invoice.getCustomer().getMobileNo(),
                invoice.getInvoiceDate(),
                rupees(invoice.getSubtotalPaise()),
                rupees(invoice.getGstAmountPaise()),
                rupees(invoice.getTotalPaise()),
                invoice.getCoupon() != null ? invoice.getCoupon().getCode() : null,
                invoice.getDiscountPaise() != null && invoice.getDiscountPaise() > 0
                        ? rupees(invoice.getDiscountPaise()) : null,
                productDiscount(invoice),
                rupees(invoice.getPaidPaise()),
                rupees(invoice.getBalancePaise()),
                invoice.getStatus(),
                invoice.getRemarks(),
                invoice.getTransportMode(),
                invoice.getVehicleNumber(),
                invoice.getDeliveryAddress(),
                invoice.getItems().stream()
                        .sorted(Comparator.comparing(InvoiceItem::getId))
                        .map(this::toResponse)
                        .toList(),
                payments.stream().map(this::toResponse).toList(),
                invoice.getCreatedAt(),
                invoice.getBankAccount() != null ? invoice.getBankAccount().getId() : null,
                invoice.getBankAccount() != null ? invoice.getBankAccount().getLabel() : null,
                invoice.getBankAccountQr() != null ? invoice.getBankAccountQr().getId() : null);
    }

    public InvoiceSummaryResponse toSummary(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCustomer().getCustomerName(),
                invoice.getCustomer().getMobileNo(),
                invoice.getInvoiceDate(),
                rupees(invoice.getTotalPaise()),
                rupees(invoice.getBalancePaise()),
                invoice.getStatus());
    }

    /**
     * Derived, not stored: the invoice already persists each line's own
     * discount, and a second stored total is one more thing that can disagree
     * with the lines it is meant to summarise.
     */
    private String productDiscount(Invoice invoice) {
        long total = invoice.getItems().stream()
                .mapToLong(item -> item.getDiscountAmountPaise() == null ? 0L : item.getDiscountAmountPaise())
                .sum();
        return total > 0 ? rupees(total) : null;
    }

    public InvoiceItemResponse toResponse(InvoiceItem item) {
        return new InvoiceItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                item.getQuantity(),
                item.getUnit(),
                rupees(item.getUnitPricePaise()),
                item.getGstRatePercent().toPlainString(),
                rupees(item.getLineSubtotalPaise()),
                rupees(item.getLineGstPaise()),
                rupees(item.getLineTotalPaise()),
                item.getDiscountType(),
                item.getDiscountPercent().toPlainString(),
                rupees(item.getDiscountAmountPaise()),
                // Gross is derived, not stored: subtotal is already net of the
                // discount, so adding it back is the one honest source.
                rupees(lineGross(item)),
                item.getFreeQuantity());
    }

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                rupees(payment.getAmountPaise()),
                payment.getPaymentMethod(),
                payment.getPaymentDate(),
                payment.getNotes());
    }

    /** Cross-invoice view for GET /v1/payments - pulls the invoice/customer in for context. */
    public PaymentSummaryResponse toSummary(Payment payment) {
        Invoice invoice = payment.getInvoice();
        return new PaymentSummaryResponse(
                payment.getId(),
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCustomer().getCustomerName(),
                invoice.getCustomer().getMobileNo(),
                payment.getAmountPaise(),
                rupees(payment.getAmountPaise()),
                payment.getPaymentMethod(),
                payment.getPaymentDate(),
                payment.getNotes());
    }

    /**
     * Line gross: quantity x unit price, computed from source (BUG-FE-017).
     *
     * It used to be reconstructed as lineSubtotalPaise + discountAmountPaise,
     * which is only right while the LINE discount is the sole thing that has
     * touched the subtotal. It is not: applyCoupon allocates a coupon across
     * lines by reducing lineSubtotalPaise, and CR-049 does the same with the
     * quotation-level discount. After either, the reconstruction returns the
     * discount alone - a 3 x ₹320 line showed a gross of ₹100.
     *
     * unit price and quantity are both snapshotted on the line and are never
     * rewritten, so they are the one honest source.
     */
    private static long lineGross(com.hardware.erp.invoice.entity.InvoiceItem item) {
        return java.math.BigDecimal.valueOf(item.getUnitPricePaise())
                .multiply(item.getQuantity())
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
    }

    private String rupees(Long paise) {
        return IndianCurrencyFormat.rupees(paise);
    }
}
