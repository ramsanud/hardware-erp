package com.hardware.erp.deliverychallan.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
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
import com.hardware.erp.deliverychallan.dto.DeliveryChallanSummaryResponse;
import com.hardware.erp.deliverychallan.entity.DeliveryChallan;
import com.hardware.erp.deliverychallan.entity.DeliveryChallanItem;
import com.hardware.erp.deliverychallan.entity.DeliveryChallanStatus;
import com.hardware.erp.deliverychallan.mapper.DeliveryChallanMapper;
import com.hardware.erp.deliverychallan.repository.DeliveryChallanRepository;
import com.hardware.erp.deliverychallan.service.DeliveryChallanService;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.invoice.dto.InvoiceItemRequest;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.service.InvoiceService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
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

/**
 * Depends On:
 *   Inventory - StockService.applyMovement moves stock OUT (DELIVERY) the
 *   moment a challan is issued, because goods have genuinely left the
 *   shop - unlike Quotation/Sales Order. Cancelling reverses it
 *   (DELIVERY_REVERSAL).
 *   Invoice - convertToInvoice() first reverses the challan's own
 *   DELIVERY movement, then calls InvoiceService.create(), which takes
 *   the SAME stock again as a normal SALE. The Invoice module is not
 *   touched at all; the net stock effect is exactly one unit out, and the
 *   ledger honestly shows both the original dispatch and its conversion.
 */
@Service
@RequiredArgsConstructor
public class DeliveryChallanServiceImpl implements DeliveryChallanService {

    private static final String MODULE = "DELIVERY_CHALLAN";
    private static final String ENTITY = "DELIVERY_CHALLAN";

    private final DeliveryChallanRepository deliveryChallanRepository;
    private final DocumentSequenceService documentSequenceService;
    private final CustomerLookupService customerLookupService;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final DeliveryChallanMapper deliveryChallanMapper;
    private final ActivityLogService activityLog;
    private final StockService stockService;
    private final InvoiceService invoiceService;
    private final IdempotencyService idempotencyService;

    @Override
    @Transactional
    public DeliveryChallanResponse create(DeliveryChallanRequest request, String idempotencyKey) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return doCreate(request, null, tenantId);
        }
        return idempotencyService.execute(tenantId, "delivery_challan.create", idempotencyKey, request,
                DeliveryChallanResponse.class, () -> doCreate(request, null, tenantId));
    }

    @Override
    @Transactional
    public DeliveryChallanResponse createFromSalesOrder(DeliveryChallanRequest request, Long sourceSalesOrderId, Long tenantId) {
        return doCreate(request, sourceSalesOrderId, tenantId);
    }

    private DeliveryChallanResponse doCreate(DeliveryChallanRequest request, Long sourceSalesOrderId, Long tenantId) {
        Customer customer = customerLookupService.findOrCreate(
                request.customerName().trim(), request.customerMobile().trim(),
                request.customerEmail(), request.customerGstNo(), request.customerStateCode(), tenantId);

        DeliveryChallan challan = DeliveryChallan.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .deliveryChallanNumber(nextDeliveryChallanNumber(tenantId))
                .customer(customer)
                .challanDate(LocalDate.now())
                .transportMode(blankToNull(request.transportMode()))
                .vehicleNumber(blankToNull(request.vehicleNumber()))
                .deliveryAddress(blankToNull(request.deliveryAddress()))
                .remarks(request.remarks())
                .sourceSalesOrderId(sourceSalesOrderId)
                .build();

        long totalValue = 0L;
        for (DeliveryChallanItemRequest itemRequest : request.items()) {
            DeliveryChallanItem item = buildLine(itemRequest, tenantId);
            item.setDeliveryChallan(challan);
            challan.getItems().add(item);
            totalValue += item.getValuePaise();
        }
        challan.setTotalValuePaise(totalValue);

        DeliveryChallan saved = deliveryChallanRepository.save(challan);

        // Stock leaves after the challan has an id, so the movement's
        // reference_id points at a row that already exists (mirrors
        // InvoiceServiceImpl.create()).
        for (DeliveryChallanItem item : saved.getItems()) {
            stockService.applyMovement(item.getProduct().getId(), item.getQuantity().negate(),
                    MovementType.DELIVERY, "DELIVERY_CHALLAN", saved.getId(), null);
        }

        Map<String, Object> logged = new LinkedHashMap<>();
        logged.put("deliveryChallanNumber", saved.getDeliveryChallanNumber());
        logged.put("totalValuePaise", saved.getTotalValuePaise());
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getDeliveryChallanNumber(), logged);

        return deliveryChallanMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryChallanResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return deliveryChallanMapper.toResponse(require(id, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DeliveryChallanSummaryResponse> search(String search, DeliveryChallanStatus status,
                                                                 LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                deliveryChallanRepository.search(tenantId, search, status, fromDate, toDate, pageable),
                deliveryChallanMapper::toSummary);
    }

    @Override
    @Transactional
    public DeliveryChallanResponse cancel(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        DeliveryChallan challan = require(id, tenantId);

        if (challan.getStatus() != DeliveryChallanStatus.ISSUED) {
            throw new BusinessException(
                    "A " + challan.getStatus() + " delivery challan cannot be cancelled");
        }

        for (DeliveryChallanItem item : challan.getItems()) {
            stockService.applyMovement(item.getProduct().getId(), item.getQuantity(),
                    MovementType.DELIVERY_REVERSAL, "DELIVERY_CHALLAN", challan.getId(),
                    "Delivery challan " + challan.getDeliveryChallanNumber() + " cancelled");
        }

        challan.setStatus(DeliveryChallanStatus.CANCELLED);
        DeliveryChallan saved = deliveryChallanRepository.save(challan);

        activityLog.deleted(MODULE, ENTITY, saved.getId(), saved.getDeliveryChallanNumber(),
                "Delivery challan cancelled, stock restored");

        return deliveryChallanMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DeliveryChallanResponse convertToInvoice(Long id, String idempotencyKey) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return doConvertToInvoice(id, tenantId);
        }
        return idempotencyService.execute(tenantId, "delivery_challan.convert_to_invoice", idempotencyKey, id,
                DeliveryChallanResponse.class, () -> doConvertToInvoice(id, tenantId));
    }

    private DeliveryChallanResponse doConvertToInvoice(Long id, Long tenantId) {
        DeliveryChallan challan = require(id, tenantId);

        if (challan.getStatus() != DeliveryChallanStatus.ISSUED) {
            throw new BusinessException(
                    "A " + challan.getStatus() + " delivery challan cannot be converted to an invoice");
        }

        // Reverse this challan's own DELIVERY movement first - see this
        // class's header comment for why. InvoiceService.create() below
        // takes the same stock again as a normal SALE.
        for (DeliveryChallanItem item : challan.getItems()) {
            stockService.applyMovement(item.getProduct().getId(), item.getQuantity(),
                    MovementType.DELIVERY_REVERSAL, "DELIVERY_CHALLAN", challan.getId(),
                    "Converted to invoice");
        }

        Customer customer = challan.getCustomer();
        var items = challan.getItems().stream()
                .map(item -> new InvoiceItemRequest(item.getProduct().getId(), item.getQuantity()))
                .toList();

        InvoiceRequest invoiceRequest = new InvoiceRequest(
                customer.getCustomerName(), customer.getMobileNo(), customer.getEmail(),
                customer.getGstNo(), customer.getStateCode(), items,
                null, null, "Converted from delivery challan " + challan.getDeliveryChallanNumber());

        InvoiceResponse invoice = invoiceService.create(invoiceRequest);

        challan.setStatus(DeliveryChallanStatus.CONVERTED);
        challan.setConvertedInvoiceId(invoice.id());
        DeliveryChallan saved = deliveryChallanRepository.save(challan);

        activityLog.action(MODULE, ENTITY, saved.getId(), saved.getDeliveryChallanNumber(),
                com.hardware.erp.common.activity.ActivityAction.UPDATE,
                "Converted to invoice " + invoice.invoiceNumber());

        return deliveryChallanMapper.toResponse(saved);
    }

    // ---------------------------------------------------------------

    private DeliveryChallanItem buildLine(DeliveryChallanItemRequest request, Long tenantId) {
        Product product = productRepository.findByIdAndTenantId(request.productId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));
        if (!product.isActive()) {
            throw new BusinessException("'" + product.getProductName() + "' is not active and cannot be dispatched");
        }

        BigDecimal quantity = request.quantity();
        long unitPricePaise = product.getSellingPricePaise();
        long valuePaise = BigDecimal.valueOf(unitPricePaise)
                .multiply(quantity)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        return DeliveryChallanItem.builder()
                .product(product)
                .productNameSnapshot(product.getProductName())
                .quantity(quantity)
                .unit(product.getUnit())
                .unitPricePaise(unitPricePaise)
                .valuePaise(valuePaise)
                .build();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String nextDeliveryChallanNumber(Long tenantId) {
        return documentSequenceService.next(DocumentType.DELIVERY_CHALLAN, tenantId);
    }

    private DeliveryChallan require(Long id, Long tenantId) {
        return deliveryChallanRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery challan", id));
    }
}
