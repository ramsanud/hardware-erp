package com.hardware.erp.invoice.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.coupon.dto.CouponDiscountResult;
import com.hardware.erp.coupon.repository.CouponRepository;
import com.hardware.erp.coupon.service.CouponService;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.service.CustomerLookupService;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.invoice.dto.*;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.InvoiceItem;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import com.hardware.erp.invoice.entity.Payment;
import com.hardware.erp.invoice.mapper.InvoiceMapper;
import com.hardware.erp.invoice.pdf.InvoicePdfService;
import com.hardware.erp.invoice.repository.InvoiceRepository;
import com.hardware.erp.invoice.repository.PaymentRepository;
import com.hardware.erp.invoice.service.InvoiceService;
import com.hardware.erp.notification.service.NotificationService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantBankAccount;
import com.hardware.erp.tenant.entity.TenantBankAccountQr;
import com.hardware.erp.tenant.repository.TenantBankAccountQrRepository;
import com.hardware.erp.tenant.repository.TenantBankAccountRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.repository.TenantLogoRepository;
import com.hardware.erp.tenant.repository.TenantSignatureRepository;
import com.hardware.erp.tenant.repository.TenantUpiQrRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Depends On:
 *   Inventory - StockService.applyMovement decrements stock on create and
 *   restores it on cancel, in the same transaction, so an invoice and its
 *   stock movement commit or roll back together (CR-021 / PROJECT_SKILLS #22
 *   - this is exactly why Inventory had to exist before Invoice).
 *   Customer - a minimal row is found-or-created by mobile number here.
 *   There is no CustomerController; this service is the only writer.
 */
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private static final String MODULE = "INVOICE";
    private static final String ENTITY = "INVOICE";

    private final InvoiceRepository invoiceRepository;
    private final DocumentSequenceService documentSequenceService;
    private final PaymentRepository paymentRepository;
    private final CustomerLookupService customerLookupService;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final InvoiceMapper invoiceMapper;
    private final ActivityLogService activityLog;
    private final StockService stockService;
    private final InvoicePdfService invoicePdfService;
    private final TenantSignatureRepository tenantSignatureRepository;
    private final TenantLogoRepository tenantLogoRepository;
    private final TenantUpiQrRepository tenantUpiQrRepository;
    private final NotificationService notificationService;
    private final CouponService couponService;
    private final CouponRepository couponRepository;
    private final TenantBankAccountRepository tenantBankAccountRepository;
    private final TenantBankAccountQrRepository tenantBankAccountQrRepository;

    @Override
    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();

        Customer customer = customerLookupService.findOrCreate(
                request.customerName().trim(), request.customerMobile().trim(),
                request.customerEmail(), request.customerGstNo(), request.customerStateCode(), tenantId);

        TenantBankAccount bankAccount = resolveBankAccount(request.bankAccountId(), tenantId);
        TenantBankAccountQr bankAccountQr = resolveBankAccountQr(request.bankAccountQrId(), bankAccount, tenantId);

        Invoice invoice = Invoice.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .invoiceNumber(nextInvoiceNumber(tenantId))
                .customer(customer)
                .invoiceDate(LocalDate.now())
                .remarks(request.remarks())
                .transportMode(blankToNull(request.transportMode()))
                .vehicleNumber(blankToNull(request.vehicleNumber()))
                .deliveryAddress(blankToNull(request.deliveryAddress()))
                .bankAccount(bankAccount)
                .bankAccountQr(bankAccountQr)
                .build();

        long subtotal = 0L;
        long gstTotal = 0L;
        for (InvoiceItemRequest itemRequest : request.items()) {
            InvoiceItem item = buildLine(itemRequest, tenantId);
            item.setInvoice(invoice);
            invoice.getItems().add(item);
            subtotal += item.getLineSubtotalPaise();
            gstTotal += item.getLineGstPaise();
        }

        long discountPaise = 0L;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            CouponDiscountResult discount = applyCoupon(invoice, request.couponCode());
            discountPaise = discount.totalDiscountPaise();
            subtotal = invoice.getItems().stream().mapToLong(InvoiceItem::getLineSubtotalPaise).sum();
            gstTotal = invoice.getItems().stream().mapToLong(InvoiceItem::getLineGstPaise).sum();
        }
        long total = subtotal + gstTotal;

        Long initialPayment = request.initialPaymentPaise();
        if (initialPayment != null && initialPayment > total) {
            throw new BusinessException(
                    "Initial payment cannot exceed the invoice total",
                    HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_EXCEEDS_TOTAL");
        }
        if (initialPayment != null && initialPayment > 0 && request.paymentMethod() == null) {
            throw new BusinessException("A payment method is required for the initial payment");
        }

        invoice.setSubtotalPaise(subtotal);
        invoice.setGstAmountPaise(gstTotal);
        invoice.setTotalPaise(total);
        invoice.setDiscountPaise(discountPaise);
        invoice.setPaidPaise(0L);
        invoice.recalculate();

        Invoice saved = invoiceRepository.save(invoice);

        // Usage is only ever recorded once the invoice that used the coupon
        // has actually saved - a request that fails validation after this
        // point (e.g. the payment-exceeds-total check below) must not have
        // consumed one of the coupon's limited uses.
        if (saved.getCoupon() != null) {
            couponService.recordUsage(saved.getCoupon().getId());
        }

        // Stock leaves after the invoice has an id, so the movement's
        // reference_id points at a row that already exists.
        for (InvoiceItem item : saved.getItems()) {
            stockService.applyMovement(item.getProduct().getId(), item.getQuantity().negate(),
                    MovementType.SALE, "INVOICE", saved.getId(), null);
        }

        List<Payment> payments = List.of();
        if (initialPayment != null && initialPayment > 0) {
            Payment payment = paymentRepository.save(Payment.builder()
                    .tenant(saved.getTenant())
                    .invoice(saved)
                    .amountPaise(initialPayment)
                    .paymentMethod(request.paymentMethod())
                    .paymentDate(LocalDateTime.now())
                    .notes("Initial payment at invoice creation")
                    .build());
            payments = List.of(payment);
            saved.setPaidPaise(initialPayment);
            saved.recalculate();
            saved = invoiceRepository.save(saved);
        }

        Map<String, Object> logged = new LinkedHashMap<>();
        logged.put("invoiceNumber", saved.getInvoiceNumber());
        logged.put("totalPaise", saved.getTotalPaise());
        logged.put("status", saved.getStatus());
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getInvoiceNumber(), logged);

        // saved.getCustomer() is the same fully-loaded entity handed back by
        // customerLookupService above, not a lazy proxy, so it is safe to
        // read from the @Async notification thread after this transaction
        // has closed.
        notificationService.notifyInvoiceCreated(saved);

        return invoiceMapper.toResponse(saved, payments);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Invoice invoice = require(id, tenantId);
        return invoiceMapper.toResponse(invoice,
                paymentRepository.findByInvoiceIdOrderByPaymentDateAsc(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummaryResponse> search(String search, InvoiceStatus status,
                                                         LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                invoiceRepository.search(tenantId, search, status, fromDate, toDate, pageable),
                invoiceMapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Invoice invoice = require(id, tenantId);
        Tenant tenant = tenantRepository.getReferenceById(tenantId);
        return invoicePdfService.render(invoice, tenant,
                tenantSignatureRepository.findById(tenantId).orElse(null),
                tenantLogoRepository.findById(tenantId).orElse(null),
                tenantUpiQrRepository.findById(tenantId).orElse(null));
    }

    /** Null is a valid, common choice - "use the shop's default bank fields," not an error. */
    private TenantBankAccount resolveBankAccount(Long bankAccountId, Long tenantId) {
        if (bankAccountId == null) {
            return null;
        }
        return tenantBankAccountRepository.findByIdAndTenantId(bankAccountId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account", bankAccountId));
    }

    private TenantBankAccountQr resolveBankAccountQr(Long qrId, TenantBankAccount bankAccount, Long tenantId) {
        if (qrId == null) {
            return null;
        }
        TenantBankAccountQr qr = tenantBankAccountQrRepository.findByIdAndTenantId(qrId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("QR code", qrId));
        if (bankAccount == null || !qr.getBankAccount().getId().equals(bankAccount.getId())) {
            throw new BusinessException("The selected QR code does not belong to the selected bank account");
        }
        return qr;
    }

    @Override
    @Transactional
    public InvoiceResponse addPayment(Long invoiceId, PaymentRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Invoice invoice = require(invoiceId, tenantId);

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("A cancelled invoice cannot take a payment");
        }
        long newPaid = invoice.getPaidPaise() + request.amountPaise();
        if (newPaid > invoice.getTotalPaise()) {
            throw new BusinessException(
                    "This payment would take the invoice over its total",
                    HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_EXCEEDS_TOTAL");
        }

        Payment payment = paymentRepository.save(Payment.builder()
                .tenant(invoice.getTenant())
                .invoice(invoice)
                .amountPaise(request.amountPaise())
                .paymentMethod(request.paymentMethod())
                .paymentDate(LocalDateTime.now())
                .notes(request.notes())
                .build());

        invoice.setPaidPaise(newPaid);
        invoice.recalculate();
        Invoice saved = invoiceRepository.save(invoice);

        activityLog.action(MODULE, "PAYMENT", payment.getId(), saved.getInvoiceNumber(),
                com.hardware.erp.common.activity.ActivityAction.CREATE,
                "Payment recorded, new status " + saved.getStatus());

        // Unlike create(), invoice.customer here came from the repository
        // as a lazy proxy that has never been touched. It must be
        // initialized while this transaction's session is still open -
        // notifyPaymentReceived runs @Async on a thread with no session,
        // and a first touch there would throw LazyInitializationException.
        Hibernate.initialize(saved.getCustomer());
        notificationService.notifyPaymentReceived(saved, payment);

        return invoiceMapper.toResponse(saved,
                paymentRepository.findByInvoiceIdOrderByPaymentDateAsc(invoiceId));
    }

    /**
     * Amends an invoice that has not been paid against yet - the "customer
     * remembered one more item before leaving" case.
     *
     * Deliberately refuses once any payment exists. This is a GST tax invoice:
     * altering figures the customer has already settled against is what credit
     * and debit notes are for, and the money already recorded would no longer
     * reconcile with the total. UNPAID with zero payments is the only window
     * where an in-place amendment is both safe and honest.
     *
     * Invoice number and date are preserved - the whole point is that this is
     * still the same bill, not a second one. Stock is applied as a *delta* per
     * product rather than a full reverse-and-reapply: reversing everything and
     * re-taking it would write two misleading movements for every unchanged
     * line, and would spuriously fail the stock guard for a line whose quantity
     * did not move at all.
     */
    @Override
    @Transactional
    public InvoiceResponse update(Long id, InvoiceRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Invoice invoice = require(id, tenantId);

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("A cancelled invoice cannot be edited.");
        }
        // Both the derived total and the payment rows are checked: paidPaise is
        // a cached figure, so trusting it alone would let a zero-value or
        // mis-summed payment row slip through the guard.
        boolean hasPayment = (invoice.getPaidPaise() != null && invoice.getPaidPaise() > 0)
                || !paymentRepository.findByInvoiceIdOrderByPaymentDateAsc(invoice.getId()).isEmpty();
        if (hasPayment) {
            throw new BusinessException(
                    "This invoice already has a payment recorded against it and cannot be edited. "
                            + "Raise a separate invoice for the additional items.",
                    HttpStatus.UNPROCESSABLE_ENTITY, "INVOICE_ALREADY_PAID");
        }

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("totalPaise", invoice.getTotalPaise());
        before.put("itemCount", invoice.getItems().size());

        // Quantity per product as it stands now, to diff against the new basket.
        Map<Long, BigDecimal> previousQty = new LinkedHashMap<>();
        for (InvoiceItem item : invoice.getItems()) {
            previousQty.merge(item.getProduct().getId(), item.getQuantity(), BigDecimal::add);
        }

        Customer customer = customerLookupService.findOrCreate(
                request.customerName().trim(), request.customerMobile().trim(),
                request.customerEmail(), request.customerGstNo(), request.customerStateCode(), tenantId);

        invoice.setCustomer(customer);
        invoice.setRemarks(request.remarks());
        invoice.setTransportMode(blankToNull(request.transportMode()));
        invoice.setVehicleNumber(blankToNull(request.vehicleNumber()));
        invoice.setDeliveryAddress(blankToNull(request.deliveryAddress()));
        invoice.setBankAccount(resolveBankAccount(request.bankAccountId(), tenantId));
        invoice.setBankAccountQr(resolveBankAccountQr(request.bankAccountQrId(), invoice.getBankAccount(), tenantId));

        invoice.getItems().clear();
        long subtotal = 0L;
        long gstTotal = 0L;
        Map<Long, BigDecimal> newQty = new LinkedHashMap<>();
        for (InvoiceItemRequest itemRequest : request.items()) {
            InvoiceItem item = buildLine(itemRequest, tenantId);
            item.setInvoice(invoice);
            invoice.getItems().add(item);
            subtotal += item.getLineSubtotalPaise();
            gstTotal += item.getLineGstPaise();
            newQty.merge(item.getProduct().getId(), item.getQuantity(), BigDecimal::add);
        }

        invoice.setSubtotalPaise(subtotal);
        invoice.setGstAmountPaise(gstTotal);
        invoice.setTotalPaise(subtotal + gstTotal);
        invoice.recalculate();

        Invoice saved = invoiceRepository.save(invoice);

        // One movement per product that actually changed. A positive delta means
        // more is being sold, so stock leaves; a negative delta returns it.
        Set<Long> touched = new LinkedHashSet<>(previousQty.keySet());
        touched.addAll(newQty.keySet());
        for (Long productId : touched) {
            BigDecimal wasQty = previousQty.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal nowQty = newQty.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal delta = nowQty.subtract(wasQty);
            if (delta.signum() == 0) continue;

            // Stock moves opposite to the sale in both directions: selling more
            // takes stock out (negative), selling less puts it back (positive).
            // The movement TYPE is what differs, so the ledger reads correctly.
            stockService.applyMovement(
                    productId,
                    delta.negate(),
                    delta.signum() > 0 ? MovementType.SALE : MovementType.SALE_REVERSAL,
                    "INVOICE", saved.getId(),
                    "Invoice " + saved.getInvoiceNumber() + " amended");
        }

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("totalPaise", saved.getTotalPaise());
        after.put("itemCount", saved.getItems().size());
        activityLog.updated(MODULE, ENTITY, saved.getId(), saved.getInvoiceNumber(), before, after);

        return invoiceMapper.toResponse(saved, List.of());
    }

    @Override
    @Transactional
    public InvoiceResponse cancel(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Invoice invoice = require(id, tenantId);

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("This invoice is already cancelled");
        }

        for (InvoiceItem item : invoice.getItems()) {
            stockService.applyMovement(item.getProduct().getId(), item.getQuantity(),
                    MovementType.SALE_REVERSAL, "INVOICE", invoice.getId(),
                    "Invoice " + invoice.getInvoiceNumber() + " cancelled");
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        Invoice saved = invoiceRepository.save(invoice);

        activityLog.deleted(MODULE, ENTITY, saved.getId(), saved.getInvoiceNumber(),
                "Invoice cancelled, stock restored");

        return invoiceMapper.toResponse(saved,
                paymentRepository.findByInvoiceIdOrderByPaymentDateAsc(id));
    }

    // ---------------------------------------------------------------

    /**
     * Reduces each eligible line's own subtotal/GST/total in place and
     * attaches the coupon to the invoice - does not touch invoice-level
     * totals, the caller resums those from the now-adjusted items. Grouped
     * by product id for the coupon's own eligibility/allocation math, then
     * split back across individual lines proportionally for the (rare)
     * case of the same product appearing on more than one line - a little
     * rounding slack (at most a paisa or two) is acceptable there rather
     * than a fully exact nested-remainder algorithm for an edge case this
     * minor.
     */
    private CouponDiscountResult applyCoupon(Invoice invoice, String couponCode) {
        Map<Long, Long> subtotalByProduct = new LinkedHashMap<>();
        for (InvoiceItem item : invoice.getItems()) {
            subtotalByProduct.merge(item.getProduct().getId(), item.getLineSubtotalPaise(), Long::sum);
        }

        CouponDiscountResult result = couponService.calculateDiscount(couponCode, subtotalByProduct);

        for (InvoiceItem item : invoice.getItems()) {
            Long productId = item.getProduct().getId();
            long productDiscount = result.discountPaiseByProductId().getOrDefault(productId, 0L);
            long productSubtotal = subtotalByProduct.get(productId);
            long itemDiscount = productDiscount == 0 || productSubtotal == 0 ? 0
                    : BigDecimal.valueOf(productDiscount)
                            .multiply(BigDecimal.valueOf(item.getLineSubtotalPaise()))
                            .divide(BigDecimal.valueOf(productSubtotal), 0, RoundingMode.DOWN)
                            .longValueExact();

            long newSubtotal = item.getLineSubtotalPaise() - itemDiscount;
            long newGst = BigDecimal.valueOf(newSubtotal).multiply(item.getGstRatePercent())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
            item.setLineSubtotalPaise(newSubtotal);
            item.setLineGstPaise(newGst);
            item.setLineTotalPaise(newSubtotal + newGst);
        }

        invoice.setCoupon(couponRepository.getReferenceById(result.couponId()));
        return result;
    }

    private InvoiceItem buildLine(InvoiceItemRequest request, Long tenantId) {
        Product product = productRepository.findByIdAndTenantId(request.productId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));
        if (!product.isActive()) {
            throw new BusinessException("'" + product.getProductName() + "' is not active and cannot be sold");
        }

        BigDecimal quantity = request.quantity();
        long unitPricePaise = product.getSellingPricePaise();
        BigDecimal gstRate = product.getGstRatePercent();

        long lineSubtotal = BigDecimal.valueOf(unitPricePaise)
                .multiply(quantity).setScale(0, RoundingMode.HALF_UP).longValueExact();
        long lineGst = BigDecimal.valueOf(lineSubtotal)
                .multiply(gstRate).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();

        return InvoiceItem.builder()
                .product(product)
                .productNameSnapshot(product.getProductName())
                .quantity(quantity)
                .unit(product.getUnit())
                .unitPricePaise(unitPricePaise)
                .gstRatePercent(gstRate)
                .lineSubtotalPaise(lineSubtotal)
                .lineGstPaise(lineGst)
                .lineTotalPaise(lineSubtotal + lineGst)
                .build();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String nextInvoiceNumber(Long tenantId) {
        return documentSequenceService.next(DocumentType.INVOICE, tenantId);
    }

    private Invoice require(Long id, Long tenantId) {
        return invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }
}
