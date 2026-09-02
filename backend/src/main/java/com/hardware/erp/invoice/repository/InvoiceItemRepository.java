package com.hardware.erp.invoice.repository;

import com.hardware.erp.invoice.entity.InvoiceItem;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * CR-053 backlog item 1 (Price History). Items are otherwise only read
 * through their owning Invoice aggregate - this is the one place a line is
 * queried directly, across products, for the Product Detail page's price
 * history section.
 */
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    @Query("select i from InvoiceItem i where i.product.id = :productId and i.invoice.tenant.id = :tenantId "
            + "and i.invoice.status <> :excludedStatus "
            + "order by i.invoice.invoiceDate desc, i.id desc")
    List<InvoiceItem> findRecentForProduct(@Param("productId") Long productId,
                                           @Param("tenantId") Long tenantId,
                                           @Param("excludedStatus") InvoiceStatus excludedStatus,
                                           Pageable pageable);
}
