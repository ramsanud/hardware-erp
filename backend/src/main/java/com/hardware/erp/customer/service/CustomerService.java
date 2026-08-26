package com.hardware.erp.customer.service;

import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.customer.dto.CustomerCreditCheckResponse;
import com.hardware.erp.customer.dto.CustomerFinancialSummaryResponse;
import com.hardware.erp.customer.dto.CustomerProductHistoryResponse;
import com.hardware.erp.customer.dto.CustomerRequest;
import com.hardware.erp.customer.dto.CustomerResponse;
import com.hardware.erp.customer.dto.CustomerSummaryResponse;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.invoice.dto.InvoiceSummaryResponse;
import com.hardware.erp.quotation.dto.QuotationSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CustomerService {

    CustomerResponse create(CustomerRequest request);

    CustomerResponse update(Long id, CustomerRequest request);

    CustomerResponse get(Long id);

    PageResponse<CustomerSummaryResponse> search(String search, CustomerStatus status, Pageable pageable);

    void deactivate(Long id);

    CustomerFinancialSummaryResponse financialSummary(Long id);

    PageResponse<InvoiceSummaryResponse> recentInvoices(Long id, Pageable pageable);

    PageResponse<QuotationSummaryResponse> recentQuotations(Long id, Pageable pageable);

    List<CustomerProductHistoryResponse> productHistory(Long id);

    Optional<CustomerCreditCheckResponse> creditCheckByMobile(String mobileNo);
}
