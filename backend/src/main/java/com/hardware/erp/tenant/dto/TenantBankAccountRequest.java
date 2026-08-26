package com.hardware.erp.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantBankAccountRequest(
        @NotBlank @Size(max = 100) String label,
        @NotBlank @Size(max = 200) String bankName,
        @NotBlank @Size(max = 200) String accountHolderName,
        @NotBlank @Pattern(regexp = "^[0-9]{9,18}$", message = "Account number must be 9-18 digits")
        String accountNumber,
        @NotBlank @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Enter a valid IFSC code")
        String ifscCode,
        @Pattern(regexp = "^$|^[\\w.\\-]{2,256}@[a-zA-Z][\\w]{2,64}$", message = "Enter a valid UPI ID, e.g. shopname@okicici")
        String upiId,
        boolean defaultAccount
) {}
