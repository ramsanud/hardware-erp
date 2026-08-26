package com.hardware.erp.ai.tool;

import com.hardware.erp.auth.entity.PermissionCode;
import com.hardware.erp.customer.dto.CustomerFinancialSummaryResponse;
import com.hardware.erp.customer.dto.CustomerSummaryResponse;
import com.hardware.erp.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CustomerBalanceTool implements AiTool {

    private final CustomerService customerService;

    @Override
    public String name() {
        return "get_customer_balance";
    }

    @Override
    public String description() {
        return "Looks up a customer by name or mobile number and returns their outstanding balance, "
                + "total invoiced, total paid, and invoice/quotation counts.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of("customer", "The customer's name or mobile number, as typed by the shop owner");
    }

    @Override
    public String requiredPermission() {
        return PermissionCode.CUSTOMER_VIEW;
    }

    @Override
    public String execute(Map<String, String> args) {
        String query = args.get("customer");
        if (query == null || query.isBlank()) {
            return "No customer name or mobile number was given.";
        }
        List<CustomerSummaryResponse> matches = customerService
                .search(query, null, PageRequest.of(0, 5)).content();
        if (matches.isEmpty()) {
            return "No customer found matching \"" + query + "\".";
        }
        if (matches.size() > 1) {
            String names = matches.stream()
                    .map(c -> c.customerName() + " (" + c.mobileNo() + ")")
                    .reduce((a, b) -> a + ", " + b).orElse("");
            return "More than one customer matches \"" + query + "\": " + names
                    + ". Ask which one, or search again with the exact mobile number.";
        }
        CustomerSummaryResponse customer = matches.get(0);
        CustomerFinancialSummaryResponse summary = customerService.financialSummary(customer.id());
        return customer.customerName() + " (" + customer.mobileNo() + "): outstanding balance Rs. "
                + summary.outstandingBalanceDisplay() + ", total invoiced Rs. " + summary.totalInvoicedDisplay()
                + ", total paid Rs. " + summary.totalPaidDisplay() + ", " + summary.invoiceCount()
                + " invoice(s), " + summary.quotationCount() + " quotation(s).";
    }
}
