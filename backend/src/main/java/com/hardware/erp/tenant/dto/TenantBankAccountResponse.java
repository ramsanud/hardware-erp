package com.hardware.erp.tenant.dto;

import com.hardware.erp.tenant.entity.TenantBankAccountStatus;

import java.util.List;

public record TenantBankAccountResponse(
        Long id,
        String label,
        String bankName,
        String accountHolderName,
        /** Last 4 digits only - see TenantBankAccountServiceImpl.revealAccountNumber() for the full number. */
        String accountNumberMasked,
        String ifscCode,
        String upiId,
        boolean defaultAccount,
        TenantBankAccountStatus status,
        List<TenantBankAccountQrResponse> qrCodes
) {}
