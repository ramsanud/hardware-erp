package com.hardware.erp.product.service;

import com.hardware.erp.product.dto.ProductPriceHistoryResponse;

import java.util.List;

/**
 * BUG-BE-008 - what the product module needs to know about past sales,
 * declared here so it never has to import the invoice module to ask.
 *
 * The invoice module already depends on product (a line item references a
 * product), and until this interface existed product depended straight back
 * on invoice - {@code ProductServiceImpl} injected {@code InvoiceItemRepository}
 * and {@code InvoiceStatus} for one method. That cycle meant neither package
 * could be compiled, tested or reasoned about without the other. Now the
 * dependency points one way: invoice implements this; product only calls it.
 */
public interface ProductSaleHistoryProvider {

    /** The most recent {@code limit} non-cancelled invoice lines for a product, newest first, within the tenant. */
    List<ProductPriceHistoryResponse> recentSales(Long productId, Long tenantId, int limit);
}
