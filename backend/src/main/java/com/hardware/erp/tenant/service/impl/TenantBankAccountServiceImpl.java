package com.hardware.erp.tenant.service.impl;

import com.hardware.erp.auth.entity.AuditAction;
import com.hardware.erp.auth.service.SecurityAuditService;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.image.ImageValidation;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.dto.TenantBankAccountRequest;
import com.hardware.erp.tenant.dto.TenantBankAccountResponse;
import com.hardware.erp.tenant.entity.TenantBankAccount;
import com.hardware.erp.tenant.entity.TenantBankAccountQr;
import com.hardware.erp.tenant.entity.TenantBankAccountStatus;
import com.hardware.erp.tenant.mapper.TenantBankAccountMapper;
import com.hardware.erp.tenant.repository.TenantBankAccountQrRepository;
import com.hardware.erp.tenant.repository.TenantBankAccountRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.TenantBankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantBankAccountServiceImpl implements TenantBankAccountService {

    private static final String MODULE = "SETTINGS";
    private static final String ENTITY = "TENANT_BANK_ACCOUNT";

    private final TenantBankAccountRepository bankAccountRepository;
    private final TenantBankAccountQrRepository qrRepository;
    private final TenantRepository tenantRepository;
    private final TenantBankAccountMapper mapper;
    private final ActivityLogService activityLog;
    private final SecurityAuditService securityAuditService;

    @Override
    @Transactional(readOnly = true)
    public List<TenantBankAccountResponse> list() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return bankAccountRepository
                .findByTenantIdAndStatusOrderByDefaultAccountDescLabelAsc(tenantId, TenantBankAccountStatus.ACTIVE)
                .stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public TenantBankAccountResponse create(TenantBankAccountRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        List<TenantBankAccount> existing = bankAccountRepository
                .findByTenantIdAndStatus(tenantId, TenantBankAccountStatus.ACTIVE);
        requireNoDuplicate(existing, request, null);

        boolean makeDefault = request.defaultAccount() || existing.isEmpty();
        if (makeDefault) {
            existing.forEach(a -> a.setDefaultAccount(false));
        }

        TenantBankAccount account = TenantBankAccount.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .label(request.label().trim())
                .bankName(request.bankName().trim())
                .accountHolderName(request.accountHolderName().trim())
                .accountNumber(request.accountNumber().trim())
                .ifscCode(request.ifscCode().trim().toUpperCase(java.util.Locale.ROOT))
                .upiId(blankToNull(request.upiId()))
                .defaultAccount(makeDefault)
                .status(TenantBankAccountStatus.ACTIVE)
                .build();

        TenantBankAccount saved = bankAccountRepository.save(account);
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getLabel(), snapshot(saved));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TenantBankAccountResponse update(Long id, TenantBankAccountRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        TenantBankAccount account = require(id, tenantId);
        List<TenantBankAccount> existing = bankAccountRepository
                .findByTenantIdAndStatus(tenantId, TenantBankAccountStatus.ACTIVE);
        requireNoDuplicate(existing, request, id);

        Map<String, Object> before = snapshot(account);

        if (request.defaultAccount() && !account.isDefaultAccount()) {
            existing.forEach(a -> a.setDefaultAccount(false));
            account.setDefaultAccount(true);
        } else if (!request.defaultAccount() && account.isDefaultAccount() && existing.size() > 1) {
            // Cannot leave a tenant with zero default accounts while others exist -
            // the invoice wizard needs exactly one to pre-select.
            throw new BusinessException(
                    "Another account must be made default before this one can be un-defaulted.");
        }

        account.setLabel(request.label().trim());
        account.setBankName(request.bankName().trim());
        account.setAccountHolderName(request.accountHolderName().trim());
        account.setAccountNumber(request.accountNumber().trim());
        account.setIfscCode(request.ifscCode().trim().toUpperCase(java.util.Locale.ROOT));
        account.setUpiId(blankToNull(request.upiId()));

        TenantBankAccount saved = bankAccountRepository.save(account);
        activityLog.updated(MODULE, ENTITY, id, saved.getLabel(), before, snapshot(saved));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        TenantBankAccount account = require(id, tenantId);
        account.setStatus(TenantBankAccountStatus.INACTIVE);
        boolean wasDefault = account.isDefaultAccount();
        account.setDefaultAccount(false);
        bankAccountRepository.save(account);

        if (wasDefault) {
            bankAccountRepository.findByTenantIdAndStatus(tenantId, TenantBankAccountStatus.ACTIVE)
                    .stream().findFirst()
                    .ifPresent(next -> {
                        next.setDefaultAccount(true);
                        bankAccountRepository.save(next);
                    });
        }
        activityLog.deleted(MODULE, ENTITY, id, account.getLabel(), "Bank account deactivated");
    }

    @Override
    @Transactional(readOnly = true)
    public String revealAccountNumber(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        TenantBankAccount account = require(id, tenantId);
        var actor = SecurityUtils.requireCurrentUser();
        securityAuditService.success(AuditAction.BANK_ACCOUNT_REVEALED, actor.getId(),
                actor.getFullName(), ENTITY, account.getId());
        return account.getAccountNumber();
    }

    @Override
    @Transactional
    public TenantBankAccountResponse addQr(Long bankAccountId, String label, MultipartFile file) {
        ImageValidation.validate(file, ImageValidation.PHOTO_TYPES);
        if (label == null || label.isBlank()) {
            throw new BusinessException("A label is required for the QR code, e.g. \"SBI QR\"");
        }
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        TenantBankAccount account = require(bankAccountId, tenantId);
        try {
            TenantBankAccountQr qr = TenantBankAccountQr.builder()
                    .bankAccount(account)
                    .label(label.trim())
                    .contentType(file.getContentType())
                    .fileSize((int) file.getSize())
                    .imageData(file.getBytes())
                    .build();
            qrRepository.save(qr);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded QR image", e);
        }
        TenantBankAccount reloaded = require(bankAccountId, tenantId);
        activityLog.updated(MODULE, ENTITY, bankAccountId, reloaded.getLabel(),
                Map.of(), Map.of("qrAdded", label.trim()));
        return mapper.toResponse(reloaded);
    }

    @Override
    @Transactional
    public void removeQr(Long qrId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        TenantBankAccountQr qr = qrRepository.findByIdAndTenantId(qrId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("QR code", qrId));
        Long accountId = qr.getBankAccount().getId();
        String accountLabel = qr.getBankAccount().getLabel();
        qrRepository.delete(qr);
        activityLog.updated(MODULE, ENTITY, accountId, accountLabel,
                Map.of(), Map.of("qrRemoved", qr.getLabel()));
    }

    @Override
    @Transactional(readOnly = true)
    public TenantBankAccountQr getQrImage(Long qrId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return qrRepository.findByIdAndTenantId(qrId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("QR code", qrId));
    }

    private TenantBankAccount require(Long id, Long tenantId) {
        return bankAccountRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account", id));
    }

    /**
     * accountNumber is encrypted at rest (non-deterministic ciphertext), so
     * this duplicate check can only run here, over the small already
     * tenant-scoped list, comparing decrypted values - never at the
     * database level. Same bank + same account number + same IFSC is the
     * real-world definition of "the same account already saved twice".
     */
    private void requireNoDuplicate(List<TenantBankAccount> existing, TenantBankAccountRequest request, Long excludeId) {
        boolean duplicate = existing.stream()
                .filter(a -> excludeId == null || !a.getId().equals(excludeId))
                .anyMatch(a -> a.getBankName().equalsIgnoreCase(request.bankName().trim())
                        && a.getAccountNumber().equals(request.accountNumber().trim())
                        && a.getIfscCode().equalsIgnoreCase(request.ifscCode().trim()));
        if (duplicate) {
            throw new BusinessException(
                    "This bank account is already saved.", org.springframework.http.HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
        }
    }

    private Map<String, Object> snapshot(TenantBankAccount account) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("label", account.getLabel());
        values.put("bankName", account.getBankName());
        values.put("accountHolderName", account.getAccountHolderName());
        values.put("ifscCode", account.getIfscCode());
        values.put("defaultAccount", account.isDefaultAccount());
        values.put("status", account.getStatus());
        return values;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
