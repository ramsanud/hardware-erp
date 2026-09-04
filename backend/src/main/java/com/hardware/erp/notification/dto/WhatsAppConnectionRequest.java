package com.hardware.erp.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * CR-056 phase 1 - manual credential entry. The tenant already has their
 * own WhatsApp Business Account and phone number registered with Meta
 * (through their own Meta Business Manager) and pastes what Meta gave
 * them here; see TenantWhatsAppConnectionServiceImpl.connect() for the
 * live Graph API call that verifies these before anything is saved.
 */
public record WhatsAppConnectionRequest(
        @NotBlank @Size(max = 50) String businessAccountId,
        @NotBlank @Size(max = 50) String phoneNumberId,
        @NotBlank @Size(max = 2000) String accessToken
) {}
