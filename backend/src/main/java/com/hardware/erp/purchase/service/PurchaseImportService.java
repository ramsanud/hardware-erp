package com.hardware.erp.purchase.service;

import com.hardware.erp.purchase.dto.ImportConfirmRequest;
import com.hardware.erp.purchase.dto.ImportPreviewResponse;
import com.hardware.erp.purchase.dto.ImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

public interface PurchaseImportService {

    /** Upload != database insertion (spec §45) - never writes anything. */
    ImportPreviewResponse preview(MultipartFile file);

    /** The only method in this module that persists an imported bill - one transaction, all or nothing (spec §15). */
    ImportResultResponse confirm(ImportConfirmRequest request, MultipartFile file);
}
