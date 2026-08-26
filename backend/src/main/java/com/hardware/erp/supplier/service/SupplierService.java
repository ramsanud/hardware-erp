package com.hardware.erp.supplier.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.supplier.dto.*;
import com.hardware.erp.supplier.entity.SupplierStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Depends On:
 *   Module 1 - SUPPLIER_VIEW and SUPPLIER_MANAGE permissions, and the
 *              security audit log.
 */
public interface SupplierService {

    SupplierResponse create(SupplierRequest request);

    SupplierResponse update(Long id, SupplierRequest request);

    SupplierResponse get(Long id);

    /** CR-018: the decrypted, unmasked bank account number. Audited on every call. */
    String revealBankAccountNumber(Long id);

    PageResponse<SupplierSummaryResponse> search(String search, SupplierStatus status,
                                                 String city, Pageable pageable);

    List<String> cities();

    void softDelete(Long id, Long actingUserId);

    SupplierContactResponse addContact(Long supplierId, SupplierContactRequest request);

    SupplierContactResponse updateContact(Long supplierId, Long contactId,
                                          SupplierContactRequest request);

    void deleteContact(Long supplierId, Long contactId);
}
