package com.hardware.erp.tenant.service;

import com.hardware.erp.tenant.entity.TenantLogo;
import com.hardware.erp.tenant.entity.TenantSignature;
import com.hardware.erp.tenant.entity.TenantUpiQr;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/** Backs Shop Settings' logo, signature and UPI QR image (CR-023, CR-026) - one service, three 1:1 image slots on the caller's own tenant. */
public interface TenantImageService {

    Optional<TenantLogo> getLogo();

    void uploadLogo(MultipartFile file);

    void removeLogo();

    Optional<TenantSignature> getSignature();

    void uploadSignature(MultipartFile file);

    void removeSignature();

    Optional<TenantUpiQr> getUpiQr();

    void uploadUpiQr(MultipartFile file);

    void removeUpiQr();
}
