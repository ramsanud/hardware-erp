package com.hardware.erp.tenant.mapper;

import com.hardware.erp.tenant.dto.TenantBankAccountQrResponse;
import com.hardware.erp.tenant.dto.TenantBankAccountResponse;
import com.hardware.erp.tenant.entity.TenantBankAccount;
import com.hardware.erp.tenant.entity.TenantBankAccountQr;
import org.springframework.stereotype.Component;

@Component
public class TenantBankAccountMapper {

    public TenantBankAccountResponse toResponse(TenantBankAccount account) {
        return new TenantBankAccountResponse(
                account.getId(),
                account.getLabel(),
                account.getBankName(),
                account.getAccountHolderName(),
                maskAccountNumber(account.getAccountNumber()),
                account.getIfscCode(),
                account.getUpiId(),
                account.isDefaultAccount(),
                account.getStatus(),
                account.getQrCodes().stream().map(this::toQrResponse).toList());
    }

    public TenantBankAccountQrResponse toQrResponse(TenantBankAccountQr qr) {
        return new TenantBankAccountQrResponse(qr.getId(), qr.getLabel());
    }

    /**
     * Same masking as Supplier's own bank account number (SupplierMapper) -
     * the last four digits are enough to confirm which account this is; the
     * full number is fetched separately, only when actually needed to reveal.
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
