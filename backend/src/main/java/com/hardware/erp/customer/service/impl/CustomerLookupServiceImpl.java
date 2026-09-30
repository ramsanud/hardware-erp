package com.hardware.erp.customer.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.customer.service.CustomerLookupService;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerLookupServiceImpl implements CustomerLookupService {


    private final CustomerRepository customerRepository;
    private final DocumentSequenceService documentSequenceService;
    private final TenantRepository tenantRepository;

    @Override
    @Transactional
    public Customer findOrCreate(String name, String mobile, String email,
                                  String gstNo, String stateCode, Long tenantId) {
        String cleanGstNo = blankToNull(gstNo);
        String cleanStateCode = blankToNull(stateCode);

        // BUG-BE-009 - serialise concurrent first-time invoices for the same
        // number; see CustomerRepository.lockForFindOrCreate.
        customerRepository.lockForFindOrCreate(lockKey(tenantId, mobile));
        return customerRepository.findByTenantIdAndMobileNo(tenantId, mobile)
                .map(customer -> {
                    if (cleanGstNo != null) customer.setGstNo(cleanGstNo);
                    if (cleanStateCode != null) customer.setStateCode(cleanStateCode);
                    return customer;
                })
                .orElseGet(() -> {
                    Customer customer = Customer.builder()
                            .tenant(tenantRepository.getReferenceById(tenantId))
                            .customerCode(documentSequenceService.next(DocumentType.CUSTOMER, tenantId))
                            .customerName(name)
                            .mobileNo(mobile)
                            .email(email)
                            .gstNo(cleanGstNo)
                            .stateCode(cleanStateCode)
                            .build();
                    return customerRepository.save(customer);
                });
    }

    /** Stable 64-bit key: tenant in the high word, the mobile's hash in the low word. Collisions only cost a needless wait. */
    private static long lockKey(Long tenantId, String mobile) {
        return (tenantId << 32) ^ (mobile.hashCode() & 0xffffffffL);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
