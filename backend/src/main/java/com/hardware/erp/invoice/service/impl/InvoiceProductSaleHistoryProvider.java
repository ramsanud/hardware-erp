package com.hardware.erp.invoice.service.impl;

import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import com.hardware.erp.invoice.repository.InvoiceItemRepository;
import com.hardware.erp.product.dto.ProductPriceHistoryResponse;
import com.hardware.erp.product.service.ProductSaleHistoryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BUG-BE-006 - the invoice module's answer to {@link ProductSaleHistoryProvider}.
 * The query and the exclusion of cancelled invoices (a cancelled sale never
 * really happened at that price) moved here unchanged from ProductServiceImpl.
 */
@Component
@RequiredArgsConstructor
public class InvoiceProductSaleHistoryProvider implements ProductSaleHistoryProvider {

    private final InvoiceItemRepository invoiceItemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductPriceHistoryResponse> recentSales(Long productId, Long tenantId, int limit) {
        return invoiceItemRepository.findRecentForProduct(productId, tenantId, InvoiceStatus.CANCELLED,
                        PageRequest.of(0, limit))
                .stream()
                .map(item -> new ProductPriceHistoryResponse(
                        item.getInvoice().getInvoiceDate(),
                        item.getInvoice().getInvoiceNumber(),
                        item.getInvoice().getCustomer().getCustomerName(),
                        item.getQuantity(),
                        IndianCurrencyFormat.rupees(item.getUnitPricePaise())))
                .toList();
    }
}
