package com.hardware.erp.product.service;

import com.hardware.erp.product.dto.ProductImportConfirmRequest;
import com.hardware.erp.product.dto.ProductImportPreviewResponse;
import com.hardware.erp.product.dto.ProductImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

/** Bulk product upload from CSV/Excel (CR-036) - mirrors the Purchase module's Supplier Bill Import two-step design: preview never writes, confirm is the only endpoint that does. */
public interface ProductImportService {

    ProductImportPreviewResponse preview(MultipartFile file);

    ProductImportResultResponse confirm(ProductImportConfirmRequest request);
}
