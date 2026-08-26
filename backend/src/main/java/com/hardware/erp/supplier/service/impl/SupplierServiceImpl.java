package com.hardware.erp.supplier.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.auth.entity.AuditAction;
import com.hardware.erp.auth.service.SecurityAuditService;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.supplier.dto.*;
import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.supplier.entity.SupplierContact;
import com.hardware.erp.supplier.entity.SupplierStatus;
import com.hardware.erp.supplier.mapper.SupplierMapper;
import com.hardware.erp.supplier.repository.SupplierContactRepository;
import com.hardware.erp.supplier.repository.SupplierRepository;
import com.hardware.erp.supplier.service.SupplierService;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Depends On:
 *   Common - ActivityLogService for the business change trail (CR-015).
 *            NOT SecurityAuditService: that table is for logins and token
 *            misuse, and mixing the two makes a security review unusable.
 *
 * Module 8 (Purchase) will read suppliers by id. Do not add hard deletion here:
 * purchase documents reference supplier_id permanently.
 *
 * Every method scopes by SecurityUtils.requireCurrentTenantId(), never by a
 * tenant id taken from the request (CR-016). A supplier id or contact id
 * from another shop is treated as not found, not forbidden - the same
 * pattern requireContact already used for a foreign parent, now extended to
 * a foreign tenant, so a cross-tenant guess does not even confirm the row
 * exists.
 */
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {


    private final SupplierRepository supplierRepository;
    private final DocumentSequenceService documentSequenceService;
    private final SupplierContactRepository contactRepository;
    private final SupplierMapper supplierMapper;
    private final ActivityLogService activityLog;
    private final TenantRepository tenantRepository;
    private final SecurityAuditService securityAuditService;
    private final EntitlementService entitlementService;

    private static final String MODULE = "SUPPLIER";
    private static final String ENTITY = "SUPPLIER";

    @Override
    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        entitlementService.requireCanAddSupplier();
        String code = resolveCode(request.supplierCode(), tenantId);
        String name = request.supplierName().trim();
        String gst = normaliseGst(request.gstNo());

        if (supplierRepository.existsBySupplierCodeAndTenantId(code, tenantId)) {
            throw new DuplicateResourceException("Supplier code", code);
        }
        if (supplierRepository.existsBySupplierNameIgnoreCaseAndTenantId(name, tenantId)) {
            throw new DuplicateResourceException("Supplier name", name);
        }
        if (gst != null && supplierRepository.existsByGstNoIgnoreCaseAndTenantId(gst, tenantId)) {
            throw new DuplicateResourceException("GST number", gst);
        }
        validateGstAgainstState(gst, blankToNull(request.stateCode()));

        Supplier supplier = Supplier.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .supplierCode(code)
                .supplierName(name)
                .contactPerson(blankToNull(request.contactPerson()))
                .mobileNo(request.mobileNo().trim())
                .alternateMobileNo(blankToNull(request.alternateMobileNo()))
                .email(normaliseEmail(request.email()))
                .gstNo(gst)
                .panNo(upperOrNull(request.panNo()))
                .addressLine1(blankToNull(request.addressLine1()))
                .addressLine2(blankToNull(request.addressLine2()))
                .city(blankToNull(request.city()))
                .stateCode(blankToNull(request.stateCode()))
                .pincode(blankToNull(request.pincode()))
                .paymentTermsDays(request.paymentTermsDays())
                .creditLimitPaise(request.creditLimitPaise())
                .bankAccountName(blankToNull(request.bankAccountName()))
                .bankAccountNo(blankToNull(request.bankAccountNo()))
                .bankIfsc(upperOrNull(request.bankIfsc()))
                .bankName(blankToNull(request.bankName()))
                .status(request.status())
                .remarks(blankToNull(request.remarks()))
                .build();

        Supplier saved = supplierRepository.save(supplier);
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getSupplierName(),
                snapshot(saved));
        return supplierMapper.toResponse(saved, List.of());
    }

    @Override
    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Supplier supplier = require(id, tenantId);
        // Captured before mutation so the log records what actually moved.
        Map<String, Object> before = snapshot(supplier);
        String name = request.supplierName().trim();
        String gst = normaliseGst(request.gstNo());

        if (request.supplierCode() != null && !request.supplierCode().isBlank()
                && supplierRepository.existsBySupplierCodeAndTenantIdAndIdNot(
                        request.supplierCode(), tenantId, id)) {
            throw new DuplicateResourceException("Supplier code", request.supplierCode());
        }
        if (supplierRepository.existsBySupplierNameIgnoreCaseAndTenantIdAndIdNot(name, tenantId, id)) {
            throw new DuplicateResourceException("Supplier name", name);
        }
        if (gst != null
                && supplierRepository.existsByGstNoIgnoreCaseAndTenantIdAndIdNot(gst, tenantId, id)) {
            throw new DuplicateResourceException("GST number", gst);
        }
        validateGstAgainstState(gst, blankToNull(request.stateCode()));

        if (request.supplierCode() != null && !request.supplierCode().isBlank()) {
            supplier.setSupplierCode(request.supplierCode().trim());
        }
        supplier.setSupplierName(name);
        supplier.setContactPerson(blankToNull(request.contactPerson()));
        supplier.setMobileNo(request.mobileNo().trim());
        supplier.setAlternateMobileNo(blankToNull(request.alternateMobileNo()));
        supplier.setEmail(normaliseEmail(request.email()));
        supplier.setGstNo(gst);
        supplier.setPanNo(upperOrNull(request.panNo()));
        supplier.setAddressLine1(blankToNull(request.addressLine1()));
        supplier.setAddressLine2(blankToNull(request.addressLine2()));
        supplier.setCity(blankToNull(request.city()));
        supplier.setStateCode(blankToNull(request.stateCode()));
        supplier.setPincode(blankToNull(request.pincode()));
        supplier.setPaymentTermsDays(request.paymentTermsDays());
        supplier.setCreditLimitPaise(request.creditLimitPaise());
        supplier.setBankAccountName(blankToNull(request.bankAccountName()));
        supplier.setBankAccountNo(blankToNull(request.bankAccountNo()));
        supplier.setBankIfsc(upperOrNull(request.bankIfsc()));
        supplier.setBankName(blankToNull(request.bankName()));
        supplier.setStatus(request.status());
        supplier.setRemarks(blankToNull(request.remarks()));

        Supplier saved = supplierRepository.save(supplier);
        activityLog.updated(MODULE, ENTITY, id, saved.getSupplierName(),
                before, snapshot(saved));
        return supplierMapper.toResponse(saved, contactsOf(id));
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return supplierMapper.toResponse(require(id, tenantId), contactsOf(id));
    }

    /**
     * CR-018. Deliberately logged to security_audit_log, not activity_log,
     * despite this class's own rule against mixing the two for business
     * changes (see the class comment) - a bank-account reveal isn't a
     * business record change, it's a sensitive-data access event, which is
     * exactly what the security log exists to answer "who saw this and
     * when" for. The decrypted value itself is never logged anywhere,
     * matching the "never logged" rule in SECURITY_REGISTRY.md.
     */
    @Override
    @Transactional(readOnly = true)
    public String revealBankAccountNumber(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Supplier supplier = require(id, tenantId);
        var actor = SecurityUtils.requireCurrentUser();
        securityAuditService.success(AuditAction.BANK_ACCOUNT_REVEALED, actor.getId(),
                actor.getFullName(), ENTITY, supplier.getId());
        return supplier.getBankAccountNo();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SupplierSummaryResponse> search(String search, SupplierStatus status,
                                                        String city, Pageable pageable) {
        return PageResponse.from(
                supplierRepository.search(SecurityUtils.requireCurrentTenantId(),
                        blankToNull(search), status, blankToNull(city), pageable),
                supplierMapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> cities() {
        return supplierRepository.findDistinctCities(SecurityUtils.requireCurrentTenantId());
    }

    @Override
    @Transactional
    public void softDelete(Long id, Long actingUserId) {
        Supplier supplier = require(id, SecurityUtils.requireCurrentTenantId());
        // Soft delete only. Module 8 purchase orders reference supplier_id and
        // must still resolve to a name for the life of the business.
        supplier.softDelete(actingUserId);
        supplierRepository.save(supplier);
        activityLog.deleted(MODULE, ENTITY, id, supplier.getSupplierName(),
                "Deactivated. Purchase history is retained.");
    }

    // ---------------- contacts ----------------

    @Override
    @Transactional
    public SupplierContactResponse addContact(Long supplierId, SupplierContactRequest request) {
        Supplier supplier = require(supplierId, SecurityUtils.requireCurrentTenantId());

        if (request.primary()) {
            // Must run before the insert: the partial unique index allows only
            // one primary row per supplier.
            contactRepository.clearPrimaryFor(supplierId);
        }

        SupplierContact contact = SupplierContact.builder()
                .supplier(supplier)
                .contactName(request.contactName().trim())
                .designation(blankToNull(request.designation()))
                .mobileNo(request.mobileNo().trim())
                .email(normaliseEmail(request.email()))
                .primary(request.primary())
                .build();

        return supplierMapper.toContactResponse(contactRepository.save(contact));
    }

    @Override
    @Transactional
    public SupplierContactResponse updateContact(Long supplierId, Long contactId,
                                                 SupplierContactRequest request) {
        // Confirms supplierId belongs to the caller's tenant before it is
        // trusted as the scope for the contact lookup below.
        require(supplierId, SecurityUtils.requireCurrentTenantId());
        SupplierContact contact = requireContact(supplierId, contactId);

        if (request.primary() && !contact.isPrimary()) {
            contactRepository.clearPrimaryFor(supplierId);
        }

        contact.setContactName(request.contactName().trim());
        contact.setDesignation(blankToNull(request.designation()));
        contact.setMobileNo(request.mobileNo().trim());
        contact.setEmail(normaliseEmail(request.email()));
        contact.setPrimary(request.primary());

        return supplierMapper.toContactResponse(contactRepository.save(contact));
    }

    @Override
    @Transactional
    public void deleteContact(Long supplierId, Long contactId) {
        require(supplierId, SecurityUtils.requireCurrentTenantId());
        // Contacts carry no financial history, so a hard delete is correct here.
        contactRepository.delete(requireContact(supplierId, contactId));
    }

    // ---------------- helpers ----------------

    /**
     * The fields worth tracking. Bank account number is deliberately excluded -
     * ActivityLogServiceImpl would redact it anyway, and a history table is read
     * by more people than the table it describes.
     */
    private Map<String, Object> snapshot(Supplier supplier) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("supplierCode", supplier.getSupplierCode());
        values.put("supplierName", supplier.getSupplierName());
        values.put("contactPerson", supplier.getContactPerson());
        values.put("mobileNo", supplier.getMobileNo());
        values.put("email", supplier.getEmail());
        values.put("gstNo", supplier.getGstNo());
        values.put("city", supplier.getCity());
        values.put("stateCode", supplier.getStateCode());
        values.put("paymentTermsDays", supplier.getPaymentTermsDays());
        values.put("creditLimitPaise", supplier.getCreditLimitPaise());
        values.put("status", supplier.getStatus() == null ? null : supplier.getStatus().name());
        return values;
    }

    private List<SupplierContact> contactsOf(Long supplierId) {
        return contactRepository.findBySupplierIdOrderByPrimaryDescContactNameAsc(supplierId);
    }

    private Supplier require(Long id, Long tenantId) {
        return supplierRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", id));
    }

    private SupplierContact requireContact(Long supplierId, Long contactId) {
        SupplierContact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId));
        // Ownership check: a contact id from another supplier must not be
        // editable by guessing the number.
        if (!contact.getSupplier().getId().equals(supplierId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
        return contact;
    }

    /** Generates SUP-0001, SUP-0002 ... when the caller leaves the code blank. */
    private String resolveCode(String requested, Long tenantId) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim().toUpperCase();
        }
        return documentSequenceService.next(DocumentType.SUPPLIER, tenantId);
    }

    /**
     * The first two characters of a GSTIN are the state code. If both are given
     * and they disagree, one of them is a typing error - and getting this wrong
     * means every purchase from this supplier is taxed under the wrong head.
     */
    private void validateGstAgainstState(String gstNo, String stateCode) {
        if (gstNo == null || stateCode == null) {
            return;
        }
        String gstState = gstNo.substring(0, 2);
        if (!gstState.equals(stateCode)) {
            throw new BusinessException(
                    "The GST number starts with state code %s but the address says %s. "
                    .formatted(gstState, stateCode)
                    + "Correct one of them - this decides whether purchases are taxed "
                    + "as CGST/SGST or IGST.");
        }
    }

    private String normaliseGst(String value) {
        return upperOrNull(value);
    }

    private String normaliseEmail(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    private String upperOrNull(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
