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

        // CR-049 - the quotation discount lands here, between the line sum and
        // the stored totals, and rewrites both.
        applyQuotationDiscount(quotation, request);
        subtotal = quotation.getSubtotalPaise();
        gstTotal = quotation.getGstAmountPaise();
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

        // CR-049 - the quotation discount lands here, between the line sum and
        // the stored totals, and rewrites both.
        applyQuotationDiscount(quotation, request);
        subtotal = quotation.getSubtotalPaise();
        gstTotal = quotation.getGstAmountPaise();
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
                .map(item -> toInvoiceLine(item, quotation))
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

    /**
     * Applies the whole-quotation discount on top of the per-line discounts
     * (CR-049), and writes back subtotal_paise, gst_amount_paise and the
     * per-line figures.
     *
     * ORDER, and why it is not negotiable:
     *
     *   line gross        qty x unit price
     *   line discount     CR-047, per line
     *   line net          summed -> subtotal after line discounts   <-- BASE
     *   quotation disc    a % of THAT base, or a fixed amount
     *   taxable           base - quotation discount
     *   GST               recomputed per line on the reduced value
     *   grand total       taxable + GST
     *
     * A percentage here is taken against the post-line-discount base, never
     * the original gross. Taking both against the same gross would hand the
     * customer more than the owner agreed to - the double-count this design
     * exists to prevent.
     *
     * WHY THE DISCOUNT IS SPREAD ACROSS LINES rather than simply subtracted
     * from the total: GST is a per-line figure and lines carry different
     * rates (18% on tools, 28% on some fittings). Subtracting the discount
     * from the total and re-taxing at one blended rate would produce a
     * different, wrong GST. So the discount is allocated across lines in
     * proportion to each line's net, and each line's GST is recomputed at its
     * own rate - the same technique InvoiceServiceImpl.applyCoupon already
     * uses for coupons.
     *
     * Rounding: allocation uses DOWN so the parts can never exceed the whole,
     * and the remainder (at most a few paise) is added to the largest line so
     * the line figures sum exactly to the quotation figures.
     */
    /**
     * Maps one quotation line to the invoice line it becomes (CR-049).
     *
     * WITHOUT a quotation-level discount this carries the owner's INTENT
     * unchanged - a 10% line stays 10%, so if the master price moved between
     * quoting and converting, the invoice discounts the price actually being
     * charged.
     *
     * WITH a quotation-level discount the two are FOLDED into a single fixed
     * amount per line. The reason is that Invoice has no whole-invoice manual
     * discount field of its own - its discount_paise belongs to the coupon
     * feature - so there is nowhere to put the second discount without
     * risking it being counted twice alongside a coupon.
     *
     * Folding is chosen because it is the option that cannot change the
     * price: applyQuotationDiscount has already allocated the quotation
     * discount across these lines, so each line's total discount is exactly
     * (gross - current net), and reproducing that as a fixed amount
     * reconstructs the agreed figure to the paise.
     *
     * The trade-off, stated rather than hidden: the invoice then shows one
     * combined discount per line ("₹165 off") instead of "10% line + 5%
     * quotation". The money is identical; the provenance is not. Recording
     * both separately on the invoice needs an invoice-level manual discount
     * of its own, which is a change to the Invoice module rather than this
     * one.
     */
    private InvoiceItemRequest toInvoiceLine(QuotationItem item, Quotation quotation) {
        boolean quotationDiscounted =
                quotation.getDiscountPaise() != null && quotation.getDiscountPaise() > 0;

        if (!quotationDiscounted) {
            return new InvoiceItemRequest(
                    item.getProduct().getId(), item.getQuantity(),
                    item.getDiscountType(), item.getDiscountPercent(),
                    item.getLabourPercent(), BigDecimal.ZERO);
        }

        long gross = BigDecimal.valueOf(item.getUnitPricePaise())
                .multiply(item.getQuantity())
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        if (gross <= 0) {
            return new InvoiceItemRequest(
                    item.getProduct().getId(), item.getQuantity(),
                    LineDiscount.Type.NONE, BigDecimal.ZERO, item.getLabourPercent(), BigDecimal.ZERO);
        }

        // Solve for the discount percentage that reproduces this line's agreed
        // net, given the SAME labour percentage:
        //
        //     net = gross x (1 - d) x (1 + l)
        //  =>   d = 1 - net / (gross x (1 + l))
        //
        // CR-050 retired fixed-amount discounts, so the folded quotation-level
        // discount has to be expressed as a percentage. Carrying labour through
        // unchanged keeps the owner's internal margin intact rather than
        // dissolving it into the discount.
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
                LineDiscount.Type.PERCENTAGE, effectiveDiscount, labour, BigDecimal.ZERO);
    }

    private void applyQuotationDiscount(Quotation quotation, QuotationRequest request) {
        long base = quotation.getItems().stream()
                .mapToLong(QuotationItem::getLineSubtotalPaise).sum();

        LineDiscount.Type type = request.quotationDiscountType() == null
                ? LineDiscount.Type.NONE : request.quotationDiscountType();

        // Priced against a single synthetic line whose gross is the base, so
        // the percentage/fixed/negative/over-the-total rules are the same code
        // that validates every individual line.
        LineDiscount.Priced priced = LineDiscount.price(
                java.math.BigDecimal.ONE, base, java.math.BigDecimal.ZERO,
                type, request.quotationDiscountPercent(),
                java.math.BigDecimal.ZERO, "this quotation");

        long discount = priced.discountAmountPaise();

        quotation.setDiscountType(type);
        quotation.setDiscountPercent(
                type == LineDiscount.Type.PERCENTAGE && request.quotationDiscountPercent() != null
                        ? request.quotationDiscountPercent() : java.math.BigDecimal.ZERO);
        quotation.setDiscountPaise(discount);

        if (discount == 0 || base == 0) {
            quotation.setSubtotalPaise(base);
            quotation.setGstAmountPaise(quotation.getItems().stream()
                    .mapToLong(QuotationItem::getLineGstPaise).sum());
            return;
        }

        long allocated = 0L;
        QuotationItem largest = null;
        for (QuotationItem item : quotation.getItems()) {
            if (largest == null
                    || item.getLineSubtotalPaise() > largest.getLineSubtotalPaise()) {
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
        for (QuotationItem item : quotation.getItems()) {
            long lineGst = BigDecimal.valueOf(item.getLineSubtotalPaise())
                    .multiply(item.getGstRatePercent())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
            item.setLineGstPaise(lineGst);
            item.setLineTotalPaise(item.getLineSubtotalPaise() + lineGst);
            taxable += item.getLineSubtotalPaise();
            gst += lineGst;
        }

        quotation.setSubtotalPaise(taxable);
        quotation.setGstAmountPaise(gst);
    }

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
                discountType, request.discountPercent(), request.labourPercent(),
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
                .labourPercent(request.labourPercent() == null
                        ? BigDecimal.ZERO : request.labourPercent())
                .labourAmountPaise(priced.labourAmountPaise())
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
