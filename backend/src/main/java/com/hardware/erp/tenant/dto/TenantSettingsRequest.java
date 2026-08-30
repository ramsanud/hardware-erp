package com.hardware.erp.tenant.dto;

import com.hardware.erp.tenant.entity.SubscriptionTier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * All fields optional except name (CR-022/CR-023): a shop can save a
 * partial address while they gather the rest, but a blank shop name would
 * break every screen that displays it (sidebar brand, PDF header). Only
 * gstNo/stateCode are pattern-checked since a malformed one silently
 * breaks the CGST/SGST-vs-IGST split on every invoice PDF from then on.
 */
/**
 * Every field except the shop name is optional, and the form sends "" - not
 * null - for a field the owner has cleared. @Pattern passes null but fails an
 * empty string, so each optional pattern needs the ^$| alternative or
 * clearing the field makes the whole save fail with no visible cause
 * (BUG-SET-001). bankIfsc had it from the start; the other four did not.
 */
public record TenantSettingsRequest(
        @NotBlank @Size(max = 200) String name,
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
        @Size(max = 100) String signatoryName,
        @Pattern(regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$", message = "Enter a valid 10-character PAN")
        String panNo,
        @Size(max = 15) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 200) String bankAccountName,
        @Size(max = 30) String bankAccountNo,
        @Pattern(regexp = "^$|^[A-Z]{4}0[A-Z0-9]{6}$", message = "Enter a valid IFSC code")
        String bankIfsc,
        @Size(max = 200) String bankName,
        @Size(max = 100) String upiId,
        /** Null means "leave unchanged" - not every settings save is also a plan change. Self-declared, see CR-027: no payment gateway exists yet. */
        SubscriptionTier subscriptionTier
) {}
