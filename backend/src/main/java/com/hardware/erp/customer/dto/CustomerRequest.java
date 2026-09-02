package com.hardware.erp.customer.dto;

import com.hardware.erp.customer.entity.CustomerStatus;
import jakarta.validation.constraints.*;

/**
 * CR-023: full CustomerController, superseding CR-021's find-or-create-only
 * table. mobileNo is still the identity InvoiceServiceImpl/QuotationServiceImpl
 * match on via CustomerLookupService - changing it here is allowed (an
 * owner correcting a typo), it just means the next invoice matches by the
 * new number.
 *
 * status was added in CR-030 §18-23 - Supplier and Product both let a
 * deactivated record be reactivated by editing status back to ACTIVE;
 * Customer was missing that (only DELETE /v1/customers/{id}, one-way, no
 * way back through the UI). DELETE still exists as the one-click shortcut
 * to deactivate; this field is what makes reactivation possible.
 */
public record CustomerRequest(
        @NotBlank @Size(max = 255) String customerName,
        @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a valid 10-digit mobile number")
        String mobileNo,
        @Email @Size(max = 255) String email,
        @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
                message = "Enter a valid 15-character GSTIN")
        String gstNo,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String city,
        @Pattern(regexp = "^$|^[0-9]{2}$", message = "State code is the 2-digit GST state code")
        String stateCode,
        @Pattern(regexp = "^$|^[0-9]{6}$", message = "Enter a valid 6-digit pincode")
        String pincode,
        @PositiveOrZero Long creditLimitPaise,
        @NotNull(message = "Status is required")
        CustomerStatus status,
        /** CR-056 §16 - null is treated as true (opted in), matching the column's own default for existing rows. */
        Boolean whatsappOptIn
) {}
