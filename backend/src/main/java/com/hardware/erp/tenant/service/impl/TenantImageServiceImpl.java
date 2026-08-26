package com.hardware.erp.tenant.service.impl;

import com.hardware.erp.common.image.ImageValidation;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.TenantLogo;
import com.hardware.erp.tenant.entity.TenantSignature;
import com.hardware.erp.tenant.entity.TenantUpiQr;
import com.hardware.erp.tenant.repository.TenantLogoRepository;
import com.hardware.erp.tenant.repository.TenantSignatureRepository;
import com.hardware.erp.tenant.repository.TenantUpiQrRepository;
import com.hardware.erp.tenant.service.TenantImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantImageServiceImpl implements TenantImageService {

    private final TenantLogoRepository logoRepository;
    private final TenantSignatureRepository signatureRepository;
    private final TenantUpiQrRepository upiQrRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantLogo> getLogo() {
        return logoRepository.findById(SecurityUtils.requireCurrentTenantId());
    }

    @Override
    @Transactional
    public void uploadLogo(MultipartFile file) {
        ImageValidation.validate(file, ImageValidation.PHOTO_TYPES);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        try {
            TenantLogo logo = logoRepository.findById(tenantId)
                    .orElse(TenantLogo.builder().tenantId(tenantId).build());
            logo.setContentType(file.getContentType());
            logo.setFileSize((int) file.getSize());
            logo.setImageData(file.getBytes());
            logo.setUpdatedAt(LocalDateTime.now());
            logoRepository.save(logo);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded image", e);
        }
    }

    @Override
    @Transactional
    public void removeLogo() {
        logoRepository.deleteById(SecurityUtils.requireCurrentTenantId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantSignature> getSignature() {
        return signatureRepository.findById(SecurityUtils.requireCurrentTenantId());
    }

    @Override
    @Transactional
    public void uploadSignature(MultipartFile file) {
        ImageValidation.validate(file, ImageValidation.SIGNATURE_TYPES);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        try {
            TenantSignature signature = signatureRepository.findById(tenantId)
                    .orElse(TenantSignature.builder().tenantId(tenantId).build());
            signature.setContentType(file.getContentType());
            signature.setFileSize((int) file.getSize());
            signature.setImageData(file.getBytes());
            signature.setUpdatedAt(LocalDateTime.now());
            signatureRepository.save(signature);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded image", e);
        }
    }

    @Override
    @Transactional
    public void removeSignature() {
        signatureRepository.deleteById(SecurityUtils.requireCurrentTenantId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantUpiQr> getUpiQr() {
        return upiQrRepository.findById(SecurityUtils.requireCurrentTenantId());
    }

    @Override
    @Transactional
    public void uploadUpiQr(MultipartFile file) {
        ImageValidation.validate(file, ImageValidation.PHOTO_TYPES);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        try {
            TenantUpiQr upiQr = upiQrRepository.findById(tenantId)
                    .orElse(TenantUpiQr.builder().tenantId(tenantId).build());
            upiQr.setContentType(file.getContentType());
            upiQr.setFileSize((int) file.getSize());
            upiQr.setImageData(file.getBytes());
            upiQr.setUpdatedAt(LocalDateTime.now());
            upiQrRepository.save(upiQr);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded image", e);
        }
    }

    @Override
    @Transactional
    public void removeUpiQr() {
        upiQrRepository.deleteById(SecurityUtils.requireCurrentTenantId());
    }
}
