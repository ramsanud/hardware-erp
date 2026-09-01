package com.hardware.erp.salesorder.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.common.util.LineDiscount;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.idempotency.IdempotencyService;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.service.CustomerLookupService;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanItemRequest;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanRequest;
import com.hardware.erp.deliverychallan.dto.DeliveryChallanResponse;
import com.hardware.erp.deliverychallan.service.DeliveryChallanService;
import com.hardware.erp.invoice.dto.InvoiceItemRequest;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.service.InvoiceService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.salesorder.dto.SalesOrderItemRequest;
import com.hardware.erp.salesorder.dto.SalesOrderRequest;
import com.hardware.erp.salesorder.dto.SalesOrderResponse;
import com.hardware.erp.salesorder.dto.SalesOrderSummaryResponse;
import com.hardware.erp.salesorder.entity.SalesOrder;
import com.hardware.erp.salesorder.entity.SalesOrderItem;
import com.hardware.erp.salesorder.entity.SalesOrderStatus;
import com.hardware.erp.salesorder.mapper.SalesOrderMapper;
import com.hardware.erp.salesorder.repository.SalesOrderRepository;
import com.hardware.erp.salesorder.service.SalesOrderService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Depends On:
 *   Product - for current price/GST rate at order time, exactly like
 *   Quotation (CR-022) - a Sales Order never moves stock, so there is
 *   nothing here for Inventory to be a dependency of.
 *   Invoice - convertToInvoice() builds a real InvoiceRequest and calls
 *   InvoiceService.create(), the one sanctioned path, never duplicated
 *   here. Same reasoning as QuotationServiceImpl.convert().
 *   IdempotencyService (CR-051) - wraps create() and convertToInvoice()
 *   so a double-clicked "Confirm order" or a retried request cannot
 *   create two orders or bill the same order twice.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderServiceImpl implements SalesOrderService {

    private static final String MODULE = "SALES_ORDER";
    private static final String ENTITY = "SALES_ORDER";
    private static final Set<SalesOrderStatus> CONVERTIBLE = Set.of(
            SalesOrderStatus.DRAFT, SalesOrderStatus.CONFIRMED);
    private static final Set<SalesOrderStatus> EDITABLE = Set.of(
            SalesOrderStatus.DRAFT, SalesOrderStatus.CONFIRMED);

    private final SalesOrderRepository salesOrderRepository;
    private final DocumentSequenceService documentSequenceService;
    private final CustomerLookupService customerLookupService;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final SalesOrderMapper salesOrderMapper;
    private final ActivityLogService activityLog;
    private final InvoiceService invoiceService;
    private final DeliveryChallanService deliveryChallanService;
    private final IdempotencyService idempotencyService;

    @Override
    @Transactional
    public SalesOrderResponse create(SalesOrderRequest request, String idempotencyKey) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return doCreate(request, tenantId);
        }
        return idempotencyService.execute(tenantId, "sales_order.create", idempotencyKey, request,
                SalesOrderResponse.class, () -> doCreate(request, tenantId));
    }

    private SalesOrderResponse doCreate(SalesOrderRequest request, Long tenantId) {
        Customer customer = customerLookupService.findOrCreate(
                request.customerName().trim(), request.customerMobile().trim(),
                request.customerEmail(), request.customerGstNo(), request.customerStateCode(), tenantId);

        SalesOrder order = SalesOrder.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .salesOrderNumber(nextSalesOrderNumber(tenantId))
                .customer(customer)
                .orderDate(LocalDate.now())
                .expectedDeliveryDate(request.expectedDeliveryDate())
                .remarks(request.remarks())
                .build();

        for (SalesOrderItemRequest itemRequest : request.items()) {
            SalesOrderItem item = buildLine(itemRequest, tenantId);
            item.setSalesOrder(order);
            order.getItems().add(item);
        }

        applyOrderDiscount(order, request.orderDiscountType(), request.orderDiscountPercent());
        order.setTotalPaise(order.getSubtotalPaise() + order.getGstAmountPaise());

        SalesOrder saved = salesOrderRepository.save(order);

        Map<String, Object> logged = new LinkedHashMap<>();
        logged.put("salesOrderNumber", saved.getSalesOrderNumber());
        logged.put("totalPaise", saved.getTotalPaise());
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getSalesOrderNumber(), logged);

        return salesOrderMapper.toResponse(saved);
    }

    /** Editable only while DRAFT or CONFIRMED - mirrors QuotationServiceImpl.update(), see its header comment. */
    @Override
    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SalesOrder order = require(id, tenantId);

        if (!EDITABLE.contains(order.getStatus())) {
            throw new BusinessException(
                    "This sales order is %s and can no longer be edited. Create a new one instead."
                            .formatted(order.getStatus().name().toLowerCase().replace('_', ' ')));
        }

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("totalPaise", order.getTotalPaise());
        before.put("itemCount", order.getItems().size());

        Customer customer = customerLookupService.findOrCreate(
                request.customerName().trim(), request.customerMobile().trim(),
                request.customerEmail(), request.customerGstNo(), request.customerStateCode(), tenantId);

        order.setCustomer(customer);
        order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        order.setRemarks(request.remarks());

        order.getItems().clear();
        for (SalesOrderItemRequest itemRequest : request.items()) {
            SalesOrderItem item = buildLine(itemRequest, tenantId);
            item.setSalesOrder(order);
            order.getItems().add(item);
        }

        applyOrderDiscount(order, request.orderDiscountType(), request.orderDiscountPercent());
        order.setTotalPaise(order.getSubtotalPaise() + order.getGstAmountPaise());

        SalesOrder saved = salesOrderRepository.save(order);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("totalPaise", saved.getTotalPaise());
        after.put("itemCount", saved.getItems().size());
        activityLog.updated(MODULE, ENTITY, saved.getId(), saved.getSalesOrderNumber(), before, after);

        return salesOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOrderResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return salesOrderMapper.toResponse(require(id, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SalesOrderSummaryResponse> search(String search, SalesOrderStatus status,
                                                            LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                salesOrderRepository.search(tenantId, search, status, fromDate, toDate, pageable),
                salesOrderMapper::toSummary);
    }

    @Override
    @Transactional
    public SalesOrderResponse updateStatus(Long id, SalesOrderStatus target) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SalesOrder order = require(id, tenantId);

        if (order.getStatus() == SalesOrderStatus.CONVERTED) {
            throw new BusinessException("A converted sales order's status cannot change");
        }
        if (target == SalesOrderStatus.CONVERTED) {
            throw new BusinessException("Use a convert action, not a status change, to bill this order");
        }

        order.setStatus(target);
        SalesOrder saved = salesOrderRepository.save(order);

        activityLog.action(MODULE, ENTITY, saved.getId(), saved.getSalesOrderNumber(),
                com.hardware.erp.common.activity.ActivityAction.UPDATE, "Status changed to " + target);

        return salesOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SalesOrderResponse convertToInvoice(Long id, String idempotencyKey) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return doConvertToInvoice(id, tenantId);
        }
        return idempotencyService.execute(tenantId, "sales_order.convert_to_invoice", idempotencyKey, id,
                SalesOrderResponse.class, () -> doConvertToInvoice(id, tenantId));
    }

    private SalesOrderResponse doConvertToInvoice(Long id, Long tenantId) {
        SalesOrder order = require(id, tenantId);

        if (!CONVERTIBLE.contains(order.getStatus())) {
            throw new BusinessException(
                    "A " + order.getStatus() + " sales order cannot be converted to an invoice");
        }

        Customer customer = order.getCustomer();
        var items = order.getItems().stream()
                .map(item -> toInvoiceLine(item, order))
                .toList();

        InvoiceRequest invoiceRequest = new InvoiceRequest(
                customer.getCustomerName(), customer.getMobileNo(), customer.getEmail(),
                customer.getGstNo(), customer.getStateCode(), items,
                null, null, "Converted from sales order " + order.getSalesOrderNumber());

        InvoiceResponse invoice = invoiceService.create(invoiceRequest);

        order.setStatus(SalesOrderStatus.CONVERTED);
        order.setConvertedInvoiceId(invoice.id());
        SalesOrder saved = salesOrderRepository.save(order);

        activityLog.action(MODULE, ENTITY, saved.getId(), saved.getSalesOrderNumber(),
                com.hardware.erp.common.activity.ActivityAction.UPDATE,
                "Converted to invoice " + invoice.invoiceNumber());

        return salesOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SalesOrderResponse convertToDeliveryChallan(Long id, String idempotencyKey) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return doConvertToDeliveryChallan(id, tenantId);
        }
        return idempotencyService.execute(tenantId, "sales_order.convert_to_delivery_challan", idempotencyKey, id,
                SalesOrderResponse.class, () -> doConvertToDeliveryChallan(id, tenantId));
    }

    /**
     * A Delivery Challan carries no pricing intent forward (see
     * DeliveryChallanItemRequest's header comment) - only productId and
     * quantity survive the conversion, exactly like the challan's own
     * minimal item shape. Price/discount is decided once, at the eventual
     * invoicing step.
     */
    private SalesOrderResponse doConvertToDeliveryChallan(Long id, Long tenantId) {
        SalesOrder order = require(id, tenantId);

        if (!CONVERTIBLE.contains(order.getStatus())) {
            throw new BusinessException(
                    "A " + order.getStatus() + " sales order cannot be converted to a delivery challan");
        }

        Customer customer = order.getCustomer();
        var items = order.getItems().stream()
                .map(item -> new com.hardware.erp.deliverychallan.dto.DeliveryChallanItemRequest(
                        item.getProduct().getId(), item.getQuantity()))
                .toList();

        DeliveryChallanRequest challanRequest = new DeliveryChallanRequest(
                customer.getCustomerName(), customer.getMobileNo(), customer.getEmail(),
                customer.getGstNo(), customer.getStateCode(), items,
                null, null, null, "Converted from sales order " + order.getSalesOrderNumber());

        DeliveryChallanResponse challan = deliveryChallanService.createFromSalesOrder(challanRequest, order.getId(), tenantId);

        order.setStatus(SalesOrderStatus.CONVERTED);
        order.setConvertedDeliveryChallanId(challan.id());
        SalesOrder saved = salesOrderRepository.save(order);

        activityLog.action(MODULE, ENTITY, saved.getId(), saved.getSalesOrderNumber(),
                com.hardware.erp.common.activity.ActivityAction.UPDATE,
                "Converted to delivery challan " + challan.deliveryChallanNumber());

        return salesOrderMapper.toResponse(saved);
    }

    // ---------------------------------------------------------------

    /**
     * Folds the order-level discount into each line's percentage, exactly
     * as QuotationServiceImpl.toInvoiceLine() does - see its header
     * comment for the full reasoning (Invoice has no whole-invoice manual
     * discount field of its own to receive a second, separate figure).
     */
    private InvoiceItemRequest toInvoiceLine(SalesOrderItem item, SalesOrder order) {
        boolean orderDiscounted = order.getDiscountPaise() != null && order.getDiscountPaise() > 0;

        if (!orderDiscounted) {
            return new InvoiceItemRequest(
                    item.getProduct().getId(), item.getQuantity(),
                    item.getDiscountType(), item.getDiscountPercent(),
                    item.getLabourPercent());
        }

        long gross = BigDecimal.valueOf(item.getUnitPricePaise())
                .multiply(item.getQuantity())
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        if (gross <= 0) {
            return new InvoiceItemRequest(
                    item.getProduct().getId(), item.getQuantity(),
                    LineDiscount.Type.NONE, BigDecimal.ZERO, item.getLabourPercent());
        }

        BigDecimal labour = item.getLabourPercent() == null
                ? BigDecimal.ZERO : item.getLabourPercent();
        BigDecimal labourFactor = BigDecimal.ONE.add(
                labour.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));

        BigDecimal effectiveDiscount = BigDecimal.ONE
                .subtract(BigDecimal.valueOf(item.getLineSubtotalPaise())
                        .divide(BigDecimal.valueOf(gross).multiply(labourFactor), 6, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        if (effectiveDiscount.signum() < 0) effectiveDiscount = BigDecimal.ZERO;
        if (effectiveDiscount.compareTo(BigDecimal.valueOf(100)) > 0) {
            effectiveDiscount = BigDecimal.valueOf(100);
        }

        return new InvoiceItemRequest(
                item.getProduct().getId(), item.getQuantity(),
                LineDiscount.Type.PERCENTAGE, effectiveDiscount, labour);
    }

    /** Same allocation technique as QuotationServiceImpl.applyQuotationDiscount() - see its header comment. */
    private void applyOrderDiscount(SalesOrder order, LineDiscount.Type requestedType, BigDecimal requestedPercent) {
        long base = order.getItems().stream()
                .mapToLong(SalesOrderItem::getLineSubtotalPaise).sum();

        LineDiscount.Type type = requestedType == null ? LineDiscount.Type.NONE : requestedType;

        LineDiscount.Priced priced = LineDiscount.price(
                BigDecimal.ONE, base, BigDecimal.ZERO,
                type, requestedPercent, BigDecimal.ZERO, "this sales order");

        long discount = priced.discountAmountPaise();

        order.setDiscountType(type);
        order.setDiscountPercent(
                type == LineDiscount.Type.PERCENTAGE && requestedPercent != null
                        ? requestedPercent : BigDecimal.ZERO);
        order.setDiscountPaise(discount);

        if (discount == 0 || base == 0) {
            order.setSubtotalPaise(base);
            order.setGstAmountPaise(order.getItems().stream()
                    .mapToLong(SalesOrderItem::getLineGstPaise).sum());
            return;
        }

        long allocated = 0L;
        SalesOrderItem largest = null;
        for (SalesOrderItem item : order.getItems()) {
            if (largest == null || item.getLineSubtotalPaise() > largest.getLineSubtotalPaise()) {
                largest = item;
            }
            long share = BigDecimal.valueOf(discount)
                    .multiply(BigDecimal.valueOf(item.getLineSubtotalPaise()))
                    .divide(BigDecimal.valueOf(base), 0, RoundingMode.DOWN)
                    .longValueExact();
            item.setLineSubtotalPaise(item.getLineSubtotalPaise() - share);
            allocated += share;
        }
        if (largest != null && allocated < discount) {
            largest.setLineSubtotalPaise(largest.getLineSubtotalPaise() - (discount - allocated));
        }

        long taxable = 0L;
        long gst = 0L;
        for (SalesOrderItem item : order.getItems()) {
            long lineGst = BigDecimal.valueOf(item.getLineSubtotalPaise())
                    .multiply(item.getGstRatePercent())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
            item.setLineGstPaise(lineGst);
            item.setLineTotalPaise(item.getLineSubtotalPaise() + lineGst);
            taxable += item.getLineSubtotalPaise();
            gst += lineGst;
        }

        order.setSubtotalPaise(taxable);
        order.setGstAmountPaise(gst);
    }

    private SalesOrderItem buildLine(SalesOrderItemRequest request, Long tenantId) {
        Product product = productRepository.findByIdAndTenantId(request.productId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));
        if (!product.isActive()) {
            throw new BusinessException("'" + product.getProductName() + "' is not active and cannot be ordered");
        }

        BigDecimal quantity = request.quantity();
        long unitPricePaise = product.getSellingPricePaise();
        BigDecimal gstRate = product.getGstRatePercent();

        LineDiscount.Type discountType =
                request.discountType() == null ? LineDiscount.Type.NONE : request.discountType();
        LineDiscount.Priced priced = LineDiscount.price(
                quantity, unitPricePaise, gstRate,
                discountType, request.discountPercent(), request.labourPercent(),
                product.getProductName());

        return SalesOrderItem.builder()
                .product(product)
                .productNameSnapshot(product.getProductName())
                .quantity(quantity)
                .unit(product.getUnit())
                .unitPricePaise(unitPricePaise)
                .gstRatePercent(gstRate)
                .discountType(discountType)
                .discountPercent(discountType == LineDiscount.Type.PERCENTAGE
                        && request.discountPercent() != null
                        ? request.discountPercent() : BigDecimal.ZERO)
                .discountAmountPaise(priced.discountAmountPaise())
                .labourPercent(request.labourPercent() == null
                        ? BigDecimal.ZERO : request.labourPercent())
                .labourAmountPaise(priced.labourAmountPaise())
                .lineSubtotalPaise(priced.netPaise())
                .lineGstPaise(priced.gstPaise())
                .lineTotalPaise(priced.totalPaise())
                .build();
    }

    private String nextSalesOrderNumber(Long tenantId) {
        return documentSequenceService.next(DocumentType.SALES_ORDER, tenantId);
    }

    private SalesOrder require(Long id, Long tenantId) {
        return salesOrderRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Sales order", id));
    }
}
