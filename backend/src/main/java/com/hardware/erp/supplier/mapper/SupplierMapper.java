package com.hardware.erp.supplier.mapper;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.supplier.dto.*;
import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.supplier.entity.SupplierContact;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class SupplierMapper {

    public SupplierResponse toResponse(Supplier supplier, List<SupplierContact> contacts) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getSupplierCode(),
                supplier.getSupplierName(),
                supplier.getContactPerson(),
                supplier.getMobileNo(),
                supplier.getAlternateMobileNo(),
                supplier.getEmail(),
                supplier.getGstNo(),
                supplier.getPanNo(),
                supplier.getAddressLine1(),
                supplier.getAddressLine2(),
                supplier.getCity(),
                supplier.getStateCode(),
                supplier.getPincode(),
                supplier.getPaymentTermsDays(),
                supplier.getCreditLimitPaise(),
                rupees(supplier.getCreditLimitPaise()),
                supplier.getBankAccountName(),
                maskAccountNumber(supplier.getBankAccountNo()),
                supplier.getBankIfsc(),
                supplier.getBankName(),
                supplier.getStatus(),
                supplier.getRemarks(),
                contacts.stream()
                        .sorted(Comparator.comparing(SupplierContact::isPrimary).reversed()
                                .thenComparing(SupplierContact::getContactName))
                        .map(this::toContactResponse)
                        .toList(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt());
    }

    public SupplierSummaryResponse toSummary(Supplier supplier) {
        return new SupplierSummaryResponse(
                supplier.getId(),
                supplier.getSupplierCode(),
                supplier.getSupplierName(),
                supplier.getContactPerson(),
                supplier.getMobileNo(),
                supplier.getCity(),
                supplier.getGstNo(),
                supplier.getPaymentTermsDays(),
                rupees(supplier.getCreditLimitPaise()),
                supplier.getStatus());
    }

    /** CR-058 recycle bin. Identification and the deletion date only. */
    public SupplierDeletedResponse toDeletedResponse(Supplier supplier) {
        return new SupplierDeletedResponse(
                supplier.getId(),
                supplier.getSupplierCode(),
                supplier.getSupplierName(),
                supplier.getMobileNo(),
                supplier.getCity(),
                supplier.getDeletedAt());
    }

    public SupplierContactResponse toContactResponse(SupplierContact contact) {
        return new SupplierContactResponse(
                contact.getId(),
                contact.getContactName(),
                contact.getDesignation(),
                contact.getMobileNo(),
                contact.getEmail(),
                contact.isPrimary());
    }

    /** Paise to a displayable rupee string, Indian grouping. */
    public String rupees(Long paise) {
        return IndianCurrencyFormat.rupees(paise);
    }

    /**
     * A bank account number is not needed on screen to identify the account -
     * the last four digits are enough to confirm which one it is. Returning it
     * in full puts it in every browser cache and every screenshot.
     */
    private String maskAccountNumber(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) {
            return null;
        }
        String trimmed = accountNo.trim();
        if (trimmed.length() <= 4) {
            return "X".repeat(trimmed.length());
        }
        return "X".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }
}
