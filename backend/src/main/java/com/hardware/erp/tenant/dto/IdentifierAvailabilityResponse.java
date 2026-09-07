package com.hardware.erp.tenant.dto;

/**
 * CR-062. Whether a would-be owner's login identifiers are still free.
 *
 * Both fields are true when the caller did not ask about that identifier, so a
 * caller checking only the mobile number is not told "your email is taken"
 * about a value it never sent.
 */
public record IdentifierAvailabilityResponse(
        boolean mobileAvailable,
        boolean emailAvailable
) {}
