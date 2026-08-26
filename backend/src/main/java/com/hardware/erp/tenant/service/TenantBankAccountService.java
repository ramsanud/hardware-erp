package com.hardware.erp.tenant.service;

import com.hardware.erp.tenant.dto.TenantBankAccountRequest;
import com.hardware.erp.tenant.dto.TenantBankAccountResponse;
import com.hardware.erp.tenant.entity.TenantBankAccountQr;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Backs Shop Settings' "Bank accounts" card (CR-036) - multiple receiving accounts per shop, each with its own set of uploaded QR images, selectable per invoice. */
public interface TenantBankAccountService {

    List<TenantBankAccountResponse> list();

    TenantBankAccountResponse create(TenantBankAccountRequest request);

    TenantBankAccountResponse update(Long id, TenantBankAccountRequest request);

    void delete(Long id);

    /** Full, unmasked account number - permission-gated the same as the masked list, since this is the owner's own settings. */
    String revealAccountNumber(Long id);

    TenantBankAccountResponse addQr(Long bankAccountId, String label, MultipartFile file);

    void removeQr(Long qrId);

    TenantBankAccountQr getQrImage(Long qrId);
}
