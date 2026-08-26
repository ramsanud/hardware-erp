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
                rupees(item.getLineTotalPaise()));
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

    private String rupees(Long paise) {
        return IndianCurrencyFormat.rupees(paise);
    }
}
