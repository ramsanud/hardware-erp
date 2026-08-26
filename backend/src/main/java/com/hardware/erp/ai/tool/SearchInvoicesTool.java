package com.hardware.erp.ai.tool;

import com.hardware.erp.auth.entity.PermissionCode;
import com.hardware.erp.invoice.dto.InvoiceSummaryResponse;
import com.hardware.erp.invoice.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SearchInvoicesTool implements AiTool {

    private final InvoiceService invoiceService;

    @Override
    public String name() {
        return "search_invoices";
    }

    @Override
    public String description() {
        return "Searches invoices by invoice number, customer name, or customer mobile number. "
                + "Leave the search text empty to get the most recent invoices.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of("search", "Free-text search - invoice number, customer name, or mobile number. May be empty.");
    }

    @Override
    public String requiredPermission() {
        return PermissionCode.INVOICE_VIEW;
    }

    @Override
    public String execute(Map<String, String> args) {
        String search = args.get("search");
        List<InvoiceSummaryResponse> matches = invoiceService
                .search(search == null || search.isBlank() ? null : search, null, null, null, PageRequest.of(0, 10))
                .content();
        if (matches.isEmpty()) {
            return "No invoices found" + (search != null && !search.isBlank() ? " matching \"" + search + "\"." : ".");
        }
        StringBuilder sb = new StringBuilder(matches.size() + " invoice(s):\n");
        for (InvoiceSummaryResponse row : matches) {
            sb.append("- ").append(row.invoiceNumber()).append(' ').append(row.customerName())
                    .append(" (").append(row.invoiceDate()).append("): total Rs. ").append(row.totalDisplay())
                    .append(", balance Rs. ").append(row.balanceDisplay()).append(", status ").append(row.status())
                    .append('\n');
        }
        return sb.toString();
    }
}
