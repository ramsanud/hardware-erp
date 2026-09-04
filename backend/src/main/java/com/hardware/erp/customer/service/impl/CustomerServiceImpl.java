package com.hardware.erp.customer.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.common.activity.ActivityAction;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.customer.dto.CustomerCreditCheckResponse;
import com.hardware.erp.customer.dto.CustomerFinancialSummaryResponse;
import com.hardware.erp.customer.dto.CustomerProductHistoryResponse;
import com.hardware.erp.customer.dto.CustomerRequest;
import com.hardware.erp.customer.dto.CustomerResponse;
import com.hardware.erp.customer.dto.CustomerSummaryResponse;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.customer.mapper.CustomerMapper;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.customer.service.CustomerService;
import com.hardware.erp.invoice.dto.InvoiceSummaryResponse;
import com.hardware.erp.invoice.mapper.InvoiceMapper;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.quotation.dto.QuotationSummaryResponse;
import com.hardware.erp.quotation.mapper.QuotationMapper;
import com.hardware.erp.quotation.repository.QuotationRepository;
import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Module 5 proper (CR-023), superseding CR-021's find-or-create-only
 * table - CustomerLookupService (used by Invoice/Quotation) is untouched
 * and remains the sole writer from those flows; this is the first writer
 * reachable directly by a human (CUSTOMER_VIEW/CUSTOMER_MANAGE, seeded
 * since CR-021 but never wired to an endpoint until now).
 */
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private static final String MODULE = "CUSTOMER";
    private static final String ENTITY = "CUSTOMER";

    private final CustomerRepository customerRepository;
    private final DocumentSequenceService documentSequenceService;
    private final InvoiceRepository invoiceRepository;
    private final QuotationRepository quotationRepository;
    private final TenantRepository tenantRepository;
    private final CustomerMapper customerMapper;
    private final InvoiceMapper invoiceMapper;
    private final QuotationMapper quotationMapper;
    private final ActivityLogService activityLog;
    private final EntitlementService entitlementService;

    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        entitlementService.requireCanAddCustomer();

        if (customerRepository.findByTenantIdAndMobileNo(tenantId, request.mobileNo()).isPresent()) {
            throw new DuplicateResourceException("Mobile number", request.mobileNo());
        }

        Customer customer = Customer.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .customerCode(documentSequenceService.next(DocumentType.CUSTOMER, tenantId))
                .build();
        applyRequest(customer, request);

        Customer saved = customerRepository.save(customer);

        Map<String, Object> logged = new LinkedHashMap<>();
        logged.put("customerCode", saved.getCustomerCode());
        logged.put("mobileNo", saved.getMobileNo());
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getCustomerName(), logged);

        return customerMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Customer customer = require(id, tenantId);

        customerRepository.findByTenantIdAndMobileNo(tenantId, request.mobileNo())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Mobile number", request.mobileNo());
                });

        Map<String, Object> before = snapshot(customer);
        applyRequest(customer, request);
        Customer saved = customerRepository.save(customer);

        activityLog.updated(MODULE, ENTITY, saved.getId(), saved.getCustomerName(), before, snapshot(saved));

        return customerMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return customerMapper.toResponse(require(id, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerSummaryResponse> search(String search, CustomerStatus status, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                customerRepository.search(tenantId, search, status, pageable),
                customerMapper::toSummary);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Customer customer = require(id, tenantId);
        customer.setStatus(CustomerStatus.INACTIVE);
        customerRepository.save(customer);
        activityLog.deleted(MODULE, ENTITY, id, customer.getCustomerName(), "Customer deactivated");
    }

    /**
     * CR-058, the inverse of deactivate. Customer has no deleted_at and no
     * @SQLRestriction, so an inactive customer was always still readable -
     * this is a plain status change, modelled on WorkerServiceImpl.activate
     * rather than on the Supplier/Product/User restore path, and it touches
     * nothing but the status.
     */
    @Override
    @Transactional
    public void activate(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Customer customer = require(id, tenantId);
        customer.setStatus(CustomerStatus.ACTIVE);
        customerRepository.save(customer);
        activityLog.action(MODULE, ENTITY, id, customer.getCustomerName(),
                ActivityAction.STATUS_CHANGE, "Customer reactivated");
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerFinancialSummaryResponse financialSummary(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        require(id, tenantId);
        Object[] invoiceAggregate = invoiceRepository.customerFinancialSummary(tenantId, id).get(0);
        long quotationCount = quotationRepository.countByTenantIdAndCustomerId(tenantId, id);
        return customerMapper.toFinancialSummary(invoiceAggregate, quotationCount);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummaryResponse> recentInvoices(Long id, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        require(id, tenantId);
        return PageResponse.from(
                invoiceRepository.findByCustomer(tenantId, id, pageable),
                invoiceMapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuotationSummaryResponse> recentQuotations(Long id, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        require(id, tenantId);
        return PageResponse.from(
                quotationRepository.findByCustomer(tenantId, id, pageable),
                quotationMapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerProductHistoryResponse> productHistory(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        require(id, tenantId);
        List<Object[]> rows = invoiceRepository.productPurchaseHistory(tenantId, id);
        return rows.stream().map(row -> new CustomerProductHistoryResponse(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (BigDecimal) row[4],
                IndianCurrencyFormat.rupees(((Number) row[5]).longValue()),
                ((java.sql.Date) row[6]).toLocalDate())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerCreditCheckResponse> creditCheckByMobile(String mobileNo) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return customerRepository.findByTenantIdAndMobileNo(tenantId, mobileNo)
                .map(customer -> {
                    Object[] invoiceAggregate = invoiceRepository.customerFinancialSummary(tenantId, customer.getId()).get(0);
                    long outstanding = ((Number) invoiceAggregate[3]).longValue();
                    return new CustomerCreditCheckResponse(
                            customer.getId(), customer.getCustomerName(),
                            customer.getCreditLimitPaise(), outstanding);
                });
    }

    // ---------------------------------------------------------------

    private static void applyRequest(Customer customer, CustomerRequest request) {
        customer.setCustomerName(request.customerName().trim());
        customer.setMobileNo(request.mobileNo().trim());
        customer.setEmail(blankToNull(request.email()));
        customer.setGstNo(blankToNull(request.gstNo()));
        customer.setAddressLine1(blankToNull(request.addressLine1()));
        customer.setAddressLine2(blankToNull(request.addressLine2()));
        customer.setCity(blankToNull(request.city()));
        customer.setStateCode(blankToNull(request.stateCode()));
        customer.setPincode(blankToNull(request.pincode()));
        customer.setCreditLimitPaise(request.creditLimitPaise() != null ? request.creditLimitPaise() : 0L);
        customer.setStatus(request.status());

        boolean optIn = request.whatsappOptIn() == null || request.whatsappOptIn();
        if (optIn != customer.isWhatsappOptIn()) {
            customer.setWhatsappOptInAt(java.time.LocalDateTime.now());
        }
        customer.setWhatsappOptIn(optIn);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static Map<String, Object> snapshot(Customer customer) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("customerName", customer.getCustomerName());
        values.put("mobileNo", customer.getMobileNo());
        values.put("email", customer.getEmail());
        values.put("gstNo", customer.getGstNo());
        values.put("creditLimitPaise", customer.getCreditLimitPaise());
        values.put("status", customer.getStatus());
        return values;
    }

    private Customer require(Long id, Long tenantId) {
        return customerRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }
}
