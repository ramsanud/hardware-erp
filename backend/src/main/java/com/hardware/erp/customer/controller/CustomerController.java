package com.hardware.erp.customer.controller;

import com.hardware.erp.common.dto.ApiResponse;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.customer.dto.CustomerCreditCheckResponse;
import com.hardware.erp.customer.dto.CustomerFinancialSummaryResponse;
import com.hardware.erp.customer.dto.CustomerProductHistoryResponse;
import com.hardware.erp.customer.dto.CustomerRequest;
import com.hardware.erp.customer.dto.CustomerResponse;
import com.hardware.erp.customer.dto.CustomerSummaryResponse;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.customer.service.CustomerService;
import com.hardware.erp.invoice.dto.InvoiceSummaryResponse;
import com.hardware.erp.quotation.dto.QuotationSummaryResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_MANAGE)")
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok(customerService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_VIEW)")
    public ApiResponse<PageResponse<CustomerSummaryResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CustomerStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(customerService.search(search, status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_VIEW)")
    public ApiResponse<CustomerResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(customerService.get(id));
    }

    /**
     * CR-030 - the invoice/quotation wizards take the customer as free text
     * (name/mobile), not a picker, so this is how the Review step can warn
     * about a credit limit before the sale: look the customer up by their
     * exact mobile number as it's typed. 404 (not an error the frontend
     * should surface) means no existing customer - a brand new walk-in,
     * which carries no credit limit to warn about.
     */
    @GetMapping("/credit-check")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_VIEW)")
    public ApiResponse<CustomerCreditCheckResponse> creditCheckByMobile(@RequestParam String mobile) {
        return ApiResponse.ok(customerService.creditCheckByMobile(mobile)
                .orElseThrow(() -> new ResourceNotFoundException("Customer")));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_MANAGE)")
    public ApiResponse<CustomerResponse> update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok(customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_MANAGE)")
    public void deactivate(@PathVariable Long id) {
        customerService.deactivate(id);
    }

    /**
     * CR-058. Customer carries no deleted_at, so there is nothing to "restore"
     * here and no deleted-records endpoint - an inactive customer was never
     * hidden in the first place. This is the plain inverse of deactivate,
     * shaped exactly like WorkerController.activate.
     */
    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_MANAGE)")
    public void activate(@PathVariable Long id) {
        customerService.activate(id);
    }

    @GetMapping("/{id}/financial-summary")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_VIEW)")
    public ApiResponse<CustomerFinancialSummaryResponse> financialSummary(@PathVariable Long id) {
        return ApiResponse.ok(customerService.financialSummary(id));
    }

    @GetMapping("/{id}/invoices")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_VIEW)")
    public ApiResponse<PageResponse<InvoiceSummaryResponse>> recentInvoices(
            @PathVariable Long id, @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.ok(customerService.recentInvoices(id, pageable));
    }

    /** Customer 360 (CR-030). */
    @GetMapping("/{id}/quotations")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_VIEW)")
    public ApiResponse<PageResponse<QuotationSummaryResponse>> recentQuotations(
            @PathVariable Long id, @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.ok(customerService.recentQuotations(id, pageable));
    }

    /** Customer 360 (CR-030) - what this customer has bought before, so a repeat sale never means re-typing it. */
    @GetMapping("/{id}/products")
    @PreAuthorize("hasAuthority(T(com.hardware.erp.auth.entity.PermissionCode).CUSTOMER_VIEW)")
    public ApiResponse<List<CustomerProductHistoryResponse>> productHistory(@PathVariable Long id) {
        return ApiResponse.ok(customerService.productHistory(id));
    }
}
