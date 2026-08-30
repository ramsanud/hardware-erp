package com.hardware.erp.quotation.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.common.util.LineDiscount;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.service.CustomerLookupService;
import com.hardware.erp.invoice.dto.InvoiceItemRequest;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.invoice.service.InvoiceService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.quotation.dto.QuotationItemRequest;
import com.hardware.erp.quotation.dto.QuotationRequest;
import com.hardware.erp.quotation.dto.QuotationResponse;
import com.hardware.erp.quotation.dto.QuotationSummaryResponse;
import com.hardware.erp.quotation.entity.Quotation;
import com.hardware.erp.quotation.entity.QuotationItem;
import com.hardware.erp.quotation.entity.QuotationStatus;
import com.hardware.erp.quotation.mapper.QuotationMapper;
import com.hardware.erp.quotation.pdf.QuotationPdfService;
import com.hardware.erp.quotation.repository.QuotationRepository;
import com.hardware.erp.quotation.service.QuotationService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantLogoRepository;
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
 *   Product - for current price/GST rate at quote time (Purchase/Inventory
 *   are not needed - a quotation never moves stock, see CR-022).
 *   Customer - via the same CustomerLookupService Invoice uses.
 *   Invoice - convert() builds a real InvoiceRequest and calls
 *   InvoiceService.create() so stock decrement, GST recalculation from
 *   *current* product rates and the customer-matching rule all happen
 *   through the one sanctioned path, never duplicated here.
 */
@Service
@RequiredArgsConstructor
public class QuotationServiceImpl implements QuotationService {

    private static final String MODULE = "QUOTATION";
    private static final String ENTITY = "QUOTATION";
    private static final Set<QuotationStatus> CONVERTIBLE = Set.of(
            QuotationStatus.DRAFT, QuotationStatus.SENT, QuotationStatus.ACCEPTED);

    /** Narrower than CONVERTIBLE on purpose - ACCEPTED figures are agreed, so they are convertible but not editable. */
    private static final Set<QuotationStatus> EDITABLE = Set.of(
            QuotationStatus.DRAFT, QuotationStatus.SENT);

    private final QuotationRepository quotationRepository;
    private final DocumentSequenceService documentSequenceService;
    private final CustomerLookupService customerLookupService;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final QuotationMapper quotationMapper;
    private final ActivityLogService activityLog;
    private final InvoiceService invoiceService;
    private final QuotationPdfService quotationPdfService;
    private final TenantLogoRepository tenantLogoRepository;

    @Override
    @Transactional
    public QuotationResponse create(QuotationRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();

        Customer customer = customerLookupService.findOrCreate(
                request.customerName().trim(), request.customerMobile().trim(),
                request.customerEmail(), request.customerGstNo(), request.customerStateCode(), tenantId);

        Quotation quotation = Quotation.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .quotationNumber(nextQuotationNumber(tenantId))
                .customer(customer)
                .quotationDate(LocalDate.now())
                .validUntil(request.validUntil())
                .remarks(request.remarks())
                .build();

        long subtotal = 0L;
        long gstTotal = 0L;
        for (QuotationItemRequest itemRequest : request.items()) {
            QuotationItem item = buildLine(itemRequest, tenantId);
            item.setQuotation(quotation);
            quotation.getItems().add(item);
            subtotal += item.getLineSubtotalPaise();
            gstTotal += item.getLineGstPaise();
        }

        quotation.setSubtotalPaise(subtotal);
        quotation.setGstAmountPaise(gstTotal);
        quotation.setTotalPaise(subtotal + gstTotal);

        Quotation saved = quotationRepository.save(quotation);

        Map<String, Object> logged = new LinkedHashMap<>();
        logged.put("quotationNumber", saved.getQuotationNumber());
        logged.put("totalPaise", saved.getTotalPaise());
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getQuotationNumber(), logged);

        return quotationMapper.toResponse(saved);
    }

    /**
     * Editable only while DRAFT or SENT.
     *
     * ACCEPTED is excluded because the customer has agreed to specific figures -
     * silently changing them afterwards is the thing a quotation exists to
     * prevent. CONVERTED is excluded because an invoice was already raised from
     * these lines and stock moved against them; the invoice is the live document
     * from that point. REJECTED and EXPIRED are closed records.
     *
     * Items are replaced wholesale rather than diffed: the wizard always submits
     * the full basket, and orphanRemoval on Quotation.items turns that into the
     * right DELETEs. Number and original date are preserved so an edited
     * quotation is still the same quotation.
     */
    @Override
    @Transactional
    public QuotationResponse update(Long id, QuotationRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Quotation quotation = require(id, tenantId);

        if (!EDITABLE.contains(quotation.getStatus())) {
            throw new BusinessException(
                    "This quotation is %s and can no longer be edited. Create a new one instead."
                            .formatted(quotation.getStatus().name().toLowerCase().replace('_', ' ')));
        }

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("totalPaise", quotation.getTotalPaise());
        before.put("itemCount", quotation.getItems().size());

        Customer customer = customerLookupService.findOrCreate(
                request.customerName().trim(), request.customerMobile().trim(),
                request.customerEmail(), request.customerGstNo(), request.customerStateCode(), tenantId);

        quotation.setCustomer(customer);
        quotation.setValidUntil(request.validUntil());
        quotation.setRemarks(request.remarks());

        quotation.getItems().clear();
        long subtotal = 0L;
        long gstTotal = 0L;
        for (QuotationItemRequest itemRequest : request.items()) {
            QuotationItem item = buildLine(itemRequest, tenantId);
            item.setQuotation(quotation);
            quotation.getItems().add(item);
            subtotal += item.getLineSubtotalPaise();
            gstTotal += item.getLineGstPaise();
        }

        quotation.setSubtotalPaise(subtotal);
        quotation.setGstAmountPaise(gstTotal);
        quotation.setTotalPaise(subtotal + gstTotal);

        Quotation saved = quotationRepository.save(quotation);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("totalPaise", saved.getTotalPaise());
        after.put("itemCount", saved.getItems().size());
        activityLog.updated(MODULE, ENTITY, saved.getId(), saved.getQuotationNumber(), before, after);

        return quotationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public QuotationResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return quotationMapper.toResponse(require(id, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuotationSummaryResponse> search(String search, QuotationStatus status,
                                                           LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                quotationRepository.search(tenantId, search, status, fromDate, toDate, pageable),
                quotationMapper::toSummary);
    }

    @Override
    @Transactional
    public QuotationResponse updateStatus(Long id, QuotationStatus target) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Quotation quotation = require(id, tenantId);

        if (quotation.getStatus() == QuotationStatus.CONVERTED) {
            throw new BusinessException("A converted quotation's status cannot change");
        }
        if (target == QuotationStatus.CONVERTED) {
            throw new BusinessException("Use the convert action, not a status change, to create an invoice");
        }
        if (target == QuotationStatus.EXPIRED) {
            throw new BusinessException("Expiry is computed from the valid-until date, not set manually");
        }

        quotation.setStatus(target);
        Quotation saved = quotationRepository.save(quotation);

        activityLog.action(MODULE, ENTITY, saved.getId(), saved.getQuotationNumber(),
                com.hardware.erp.common.activity.ActivityAction.UPDATE, "Status changed to " + target);

        return quotationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public QuotationResponse convert(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Quotation quotation = require(id, tenantId);

        if (!CONVERTIBLE.contains(quotation.getStatus())) {
            throw new BusinessException(
                    "A " + quotation.getStatus() + " quotation cannot be converted to an invoice");
        }
        if (quotation.isExpired()) {
            throw new BusinessException(
                    "This quotation expired on " + quotation.getValidUntil() + " and must be re-quoted");
        }

        Customer customer = quotation.getCustomer();
        var items = quotation.getItems().stream()
                .map(item -> new InvoiceItemRequest(
                        item.getProduct().getId(), item.getQuantity(),
                        // CR-047: carry the owner's INTENT, not a frozen rupee
                        // figure. A 10% line stays 10%, so if the master price
                        // moved between quote and conversion the invoice
                        // discounts the price actually being charged.
                        item.getDiscountType(), item.getDiscountPercent(),
                        item.getDiscountAmountPaise()))
                .toList();

        InvoiceRequest invoiceRequest = new InvoiceRequest(
                customer.getCustomerName(), customer.getMobileNo(), customer.getEmail(),
                customer.getGstNo(), customer.getStateCode(), items,
                null, null, "Converted from quotation " + quotation.getQuotationNumber());

        InvoiceResponse invoice = invoiceService.create(invoiceRequest);

        quotation.setStatus(QuotationStatus.CONVERTED);
        quotation.setConvertedInvoiceId(invoice.id());
        Quotation saved = quotationRepository.save(quotation);

        activityLog.action(MODULE, ENTITY, saved.getId(), saved.getQuotationNumber(),
                com.hardware.erp.common.activity.ActivityAction.UPDATE,
                "Converted to invoice " + invoice.invoiceNumber());

        return quotationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Quotation quotation = require(id, tenantId);
        Tenant tenant = tenantRepository.getReferenceById(tenantId);
        return quotationPdfService.render(quotation, tenant, tenantLogoRepository.findById(tenantId).orElse(null));
    }

    // ---------------------------------------------------------------

    private QuotationItem buildLine(QuotationItemRequest request, Long tenantId) {
        Product product = productRepository.findByIdAndTenantId(request.productId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));
        if (!product.isActive()) {
            throw new BusinessException("'" + product.getProductName() + "' is not active and cannot be quoted");
        }

        BigDecimal quantity = request.quantity();
        long unitPricePaise = product.getSellingPricePaise();
        BigDecimal gstRate = product.getGstRatePercent();

        // CR-047 - see InvoiceServiceImpl.buildLine; the same calculator, on
        // purpose, so a quotation and the invoice it becomes agree to the paise.
        LineDiscount.Type discountType =
                request.discountType() == null ? LineDiscount.Type.NONE : request.discountType();
        LineDiscount.Priced priced = LineDiscount.price(
                quantity, unitPricePaise, gstRate,
                discountType, request.discountPercent(), request.discountAmountPaise(),
                product.getProductName());

        return QuotationItem.builder()
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
                .lineSubtotalPaise(priced.netPaise())
                .lineGstPaise(priced.gstPaise())
                .lineTotalPaise(priced.totalPaise())
                .build();
    }

    private String nextQuotationNumber(Long tenantId) {
        return documentSequenceService.next(DocumentType.QUOTATION, tenantId);
    }

    private Quotation require(Long id, Long tenantId) {
        return quotationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", id));
    }
}
