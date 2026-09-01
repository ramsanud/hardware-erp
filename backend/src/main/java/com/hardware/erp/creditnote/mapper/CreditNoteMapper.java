package com.hardware.erp.creditnote.mapper;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.creditnote.dto.CreditNoteItemResponse;
import com.hardware.erp.creditnote.dto.CreditNoteResponse;
import com.hardware.erp.creditnote.dto.CreditNoteSummaryResponse;
import com.hardware.erp.creditnote.entity.CreditNote;
import com.hardware.erp.creditnote.entity.CreditNoteItem;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class CreditNoteMapper {

    public CreditNoteResponse toResponse(CreditNote creditNote) {
        return new CreditNoteResponse(
                creditNote.getId(),
                creditNote.getCreditNoteNumber(),
                creditNote.getInvoice().getId(),
                creditNote.getInvoice().getInvoiceNumber(),
                creditNote.getCustomer().getId(),
                creditNote.getCustomer().getCustomerName(),
                creditNote.getCustomer().getMobileNo(),
                creditNote.getCreditNoteDate(),
                creditNote.getReason(),
                rupees(creditNote.getSubtotalPaise()),
                rupees(creditNote.getGstAmountPaise()),
                rupees(creditNote.getTotalPaise()),
                creditNote.getStatus(),
                creditNote.getRemarks(),
                creditNote.getItems().stream()
                        .sorted(Comparator.comparing(CreditNoteItem::getId))
                        .map(this::toResponse)
                        .toList(),
                creditNote.getCreatedAt());
    }

    public CreditNoteSummaryResponse toSummary(CreditNote creditNote) {
        return new CreditNoteSummaryResponse(
                creditNote.getId(),
                creditNote.getCreditNoteNumber(),
                creditNote.getInvoice().getInvoiceNumber(),
                creditNote.getCustomer().getCustomerName(),
                creditNote.getCustomer().getMobileNo(),
                creditNote.getCreditNoteDate(),
                rupees(creditNote.getTotalPaise()),
                creditNote.getStatus());
    }

    public CreditNoteItemResponse toResponse(CreditNoteItem item) {
        return new CreditNoteItemResponse(
                item.getId(),
                item.getInvoiceItem().getId(),
                item.getProduct().getId(),
                item.getProductNameSnapshot(),
                item.getQuantity(),
                rupees(item.getUnitPricePaise()),
                item.getGstRatePercent().toPlainString(),
                rupees(item.getLineSubtotalPaise()),
                rupees(item.getLineGstPaise()),
                rupees(item.getLineTotalPaise()));
    }

    private String rupees(Long paise) {
        return IndianCurrencyFormat.rupees(paise);
    }
}
