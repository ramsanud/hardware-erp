package com.hardware.erp.customer.service;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.customer.service.impl.CustomerLookupServiceImpl;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Extracted from InvoiceServiceImplTest (CR-022) - this logic is now shared
 * by Invoice and Quotation, so it has its own test rather than being
 * re-verified twice through two different callers.
 */
@ExtendWith(MockitoExtension.class)
class CustomerLookupServiceImplTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private TenantRepository tenantRepository;

    @InjectMocks private CustomerLookupServiceImpl lookupService;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("a returning customer's mobile number reuses the existing customer, not a new one")
    void reusesExistingCustomerByMobile() {
        Customer existing = Customer.builder().id(5L).tenant(tenant).customerCode("CUS-0001")
                .customerName("Ramesh Traders").mobileNo("9876500001").build();
        when(customerRepository.findByTenantIdAndMobileNo(1L, "9876500001"))
                .thenReturn(Optional.of(existing));

        Customer result = lookupService.findOrCreate(
                "Ramesh Traders", "9876500001", null, null, null, 1L);

        assertThat(result).isSameAs(existing);
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("an unknown mobile number creates a new customer with the next generated code")
    void createsNewCustomerWhenMobileUnknown() {
        when(customerRepository.findByTenantIdAndMobileNo(1L, "9998887777"))
                .thenReturn(Optional.empty());
        when(documentSequenceService.next(DocumentType.CUSTOMER, 1L)).thenReturn("CUS-0004");
        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = lookupService.findOrCreate(
                "New Customer", "9998887777", "new@example.com", null, null, 1L);

        assertThat(result.getCustomerCode()).isEqualTo("CUS-0004");
        assertThat(result.getMobileNo()).isEqualTo("9998887777");
    }

    @Test
    @DisplayName("GST number and state code are filled in on a returning customer that had neither")
    void fillsInGstAndStateOnReturningCustomer() {
        Customer existing = Customer.builder().id(5L).tenant(tenant).customerCode("CUS-0001")
                .customerName("Ramesh Traders").mobileNo("9876500001").build();
        when(customerRepository.findByTenantIdAndMobileNo(1L, "9876500001"))
                .thenReturn(Optional.of(existing));

        Customer result = lookupService.findOrCreate(
                "Ramesh Traders", "9876500001", null, "29ABCDE1234F1Z5", "29", 1L);

        assertThat(result.getGstNo()).isEqualTo("29ABCDE1234F1Z5");
        assertThat(result.getStateCode()).isEqualTo("29");
    }

    @Test
    @DisplayName("a blank GST number does not overwrite one the customer already had")
    void blankGstDoesNotOverwriteExisting() {
        Customer existing = Customer.builder().id(5L).tenant(tenant).customerCode("CUS-0001")
                .customerName("Ramesh Traders").mobileNo("9876500001")
                .gstNo("29ABCDE1234F1Z5").build();
        when(customerRepository.findByTenantIdAndMobileNo(1L, "9876500001"))
                .thenReturn(Optional.of(existing));

        Customer result = lookupService.findOrCreate(
                "Ramesh Traders", "9876500001", null, "  ", null, 1L);

        assertThat(result.getGstNo()).isEqualTo("29ABCDE1234F1Z5");
    }

    @Test
    @DisplayName("a new customer's code number generation is scoped per tenant")
    void newCustomerCodeUsesTenantScopedSequence() {
        when(customerRepository.findByTenantIdAndMobileNo(1L, "9998887777"))
                .thenReturn(Optional.empty());
        when(documentSequenceService.next(DocumentType.CUSTOMER, 1L)).thenReturn("CUS-0001");
        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        when(customerRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        lookupService.findOrCreate("New Customer", "9998887777", null, null, null, 1L);

        assertThat(captor.getValue().getCustomerCode()).isEqualTo("CUS-0001");
    }
}
