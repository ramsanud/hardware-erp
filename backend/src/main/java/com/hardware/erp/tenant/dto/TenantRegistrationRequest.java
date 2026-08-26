package com.hardware.erp.tenant.dto;

import com.hardware.erp.tenant.entity.SubscriptionTier;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Public endpoint (CR-028, fulfills the second-tenant provisioning CR-016 deferred) - a brand-new shop signing up, not a user joining an existing one (CR-008 still blocks that). */
public record TenantRegistrationRequest(
        @NotBlank @Size(max = 200) String shopName,
        @NotBlank @Size(max = 200) String ownerFullName,
        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit mobile number")
        String mobileNo,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                 message = "Password must contain at least one letter and one number")
        String password,
        /** Null defaults to FREE - no payment gateway exists, this is a self-declared feature-gating choice (see SubscriptionService). */
        SubscriptionTier subscriptionTier,

        /**
         * Must be true. Enforced server-side (CR-040) so a client calling the
         * API directly cannot skip the agreement the UI presents.
         *
         * What this can and cannot prove is worth being precise about: it
         * records that the account was created by a caller asserting
         * acceptance of a named version. It is not evidence that a human read
         * the document, and nothing in this API could be.
         */
        @NotNull(message = "You must accept the Terms & Conditions to create an account")
        @AssertTrue(message = "You must accept the Terms & Conditions to create an account")
        Boolean termsAccepted,

        /** The version the client displayed. Rejected unless it matches the current published version. */
        @Size(max = 20) String termsVersion,

        @Size(max = 20) String privacyVersion,

        /** Optional and revocable. Null is treated as "not granted" - never as consent. */
        Boolean marketingConsent
) {}
