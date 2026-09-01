package com.hardware.erp.creditnote.service.impl;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.idempotency.IdempotencyService;
import com.hardware.erp.creditnote.dto.CreditNoteItemRequest;
import com.hardware.erp.creditnote.dto.CreditNoteRequest;
import com.hardware.erp.creditnote.dto.CreditNoteResponse;
import com.hardware.erp.creditnote.dto.CreditNoteSummaryResponse;
import com.hardware.erp.creditnote.entity.CreditNote;
import com.hardware.erp.creditnote.entity.CreditNoteItem;
import com.hardware.erp.creditnote.entity.CreditNoteStatus;
import com.hardware.erp.creditnote.mapper.CreditNoteMapper;
import com.hardware.erp.creditnote.repository.CreditNoteItemRepository;
import com.hardware.erp.creditnote.repository.CreditNoteRepository;
import com.hardware.erp.creditnote.service.CreditNoteService;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.invoice.entity.Invoice;
import com.hardware.erp.invoice.entity.InvoiceItem;
import com.hardware.erp.invoice.entity.InvoiceStatus;
import com.hardware.erp.invoice.repository.InvoiceRepository;
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
 *   Invoice - read-only. The invoice being returned against is looked up
 *   and its items read to find the exact original line and its
 *   already-charged rate; this service never writes back to Invoice or
 *   InvoiceItem. See V37's header comment for why: a settled tax invoice
 *   is never rewritten, a credit note stands beside it as its own
 *   document. "What the customer still owes, net of returns" is left as a
 *   reporting-layer question for a future CR rather than solved by
 *   mutating Invoice.paidPaise/balancePaise here.
 *   Inventory - StockService.applyMovement brings the returned quantity
 *   back into stock (SALES_RETURN) in the same transaction the credit
 *   note is created in, and reverses it (SALES_RETURN_REVERSAL) if the
 *   credit note is itself cancelled.
 *   IdempotencyService (CR-051) - wraps create() so a double-clicked
 *   "Issue credit note" cannot return the same goods twice.
 */
@Service
@RequiredArgsConstructor
public class CreditNoteServiceImpl implements CreditNoteService {

    private static final String MODULE = "CREDIT_NOTE";
    private static final String ENTITY = "CREDIT_NOTE";

    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteItemRepository creditNoteItemRepository;
    private final DocumentSequenceService documentSequenceService;
    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;
    private final CreditNoteMapper creditNoteMapper;
    private final ActivityLogService activityLog;
    private final StockService stockService;
    private final IdempotencyService idempotencyService;

    @Override
    @Transactional
    public CreditNoteResponse create(CreditNoteRequest request, String idempotencyKey) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return doCreate(request, tenantId);
        }
        return idempotencyService.execute(tenantId, "credit_note.create", idempotencyKey, request,
                CreditNoteResponse.class, () -> doCreate(request, tenantId));
    }

    private CreditNoteResponse doCreate(CreditNoteRequest request, Long tenantId) {
        Invoice invoice = invoiceRepository.findByIdAndTenantId(request.invoiceId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", request.invoiceId()));
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("A cancelled invoice has no stock or revenue left to return against");
        }

        CreditNote creditNote = CreditNote.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .creditNoteNumber(nextCreditNoteNumber(tenantId))
                .invoice(invoice)
                .customer(invoice.getCustomer())
                .creditNoteDate(LocalDate.now())
                .reason(request.reason().trim())
                .remarks(request.remarks())
                .build();

        // Tracks quantity claimed by an EARLIER line in this same request,
        // per invoiceItemId - sumCreditedQuantity() only sees already
        // COMMITTED credit notes, so two lines in one request returning the
        // same invoice line would otherwise each check against the database
        // independently and could jointly over-credit past the invoiced
        // quantity without either check ever seeing the other.
        Map<Long, BigDecimal> claimedInThisRequest = new LinkedHashMap<>();

        long subtotal = 0L;
        long gstTotal = 0L;
        for (CreditNoteItemRequest itemRequest : request.items()) {
            CreditNoteItem item = buildLine(itemRequest, invoice, claimedInThisRequest);
            item.setCreditNote(creditNote);
            creditNote.getItems().add(item);
            subtotal += item.getLineSubtotalPaise();
            gstTotal += item.getLineGstPaise();
        }
        creditNote.setSubtotalPaise(subtotal);
        creditNote.setGstAmountPaise(gstTotal);
        creditNote.setTotalPaise(subtotal + gstTotal);

        CreditNote saved = creditNoteRepository.save(creditNote);

        // Goods physically come back after the credit note has an id, so
        // the movement's reference_id points at a row that already exists
        // (mirrors InvoiceServiceImpl.create()).
        for (CreditNoteItem item : saved.getItems()) {
            stockService.applyMovement(item.getProduct().getId(), item.getQuantity(),
                    MovementType.SALES_RETURN, "CREDIT_NOTE", saved.getId(), null);
        }

        Map<String, Object> logged = new LinkedHashMap<>();
        logged.put("creditNoteNumber", saved.getCreditNoteNumber());
        logged.put("invoiceNumber", invoice.getInvoiceNumber());
        logged.put("totalPaise", saved.getTotalPaise());
        activityLog.created(MODULE, ENTITY, saved.getId(), saved.getCreditNoteNumber(), logged);

        return creditNoteMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditNoteResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return creditNoteMapper.toResponse(require(id, tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CreditNoteSummaryResponse> search(String search, CreditNoteStatus status,
                                                            LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(
                creditNoteRepository.search(tenantId, search, status, fromDate, toDate, pageable),
                creditNoteMapper::toSummary);
    }

    @Override
    @Transactional
    public CreditNoteResponse cancel(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        CreditNote creditNote = require(id, tenantId);

        if (creditNote.getStatus() == CreditNoteStatus.CANCELLED) {
            throw new BusinessException("This credit note is already cancelled");
        }

        for (CreditNoteItem item : creditNote.getItems()) {
            stockService.applyMovement(item.getProduct().getId(), item.getQuantity().negate(),
                    MovementType.SALES_RETURN_REVERSAL, "CREDIT_NOTE", creditNote.getId(),
                    "Credit note " + creditNote.getCreditNoteNumber() + " cancelled");
        }

        creditNote.setStatus(CreditNoteStatus.CANCELLED);
        CreditNote saved = creditNoteRepository.save(creditNote);

        activityLog.deleted(MODULE, ENTITY, saved.getId(), saved.getCreditNoteNumber(),
                "Credit note cancelled, stock reversed");

        return creditNoteMapper.toResponse(saved);
    }

    // ---------------------------------------------------------------

    /**
     * unitPricePaise/lineSubtotalPaise are the EFFECTIVE rate the customer
     * was actually charged on the original line - lineSubtotalPaise
     * divided across its quantity, then scaled by the quantity being
     * returned - never the product's current or gross price. This is what
     * stops a credit note from ever refunding more than was collected.
     */
    private CreditNoteItem buildLine(CreditNoteItemRequest request, Invoice invoice,
                                      Map<Long, BigDecimal> claimedInThisRequest) {
        InvoiceItem invoiceItem = invoice.getItems().stream()
                .filter(item -> item.getId().equals(request.invoiceItemId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Invoice line " + request.invoiceItemId() + " does not belong to invoice "
                                + invoice.getInvoiceNumber()));

        BigDecimal requestedQty = request.quantity();
        BigDecimal alreadyCredited = creditNoteItemRepository.sumCreditedQuantity(invoiceItem.getId());
        BigDecimal claimedSoFar = claimedInThisRequest.getOrDefault(invoiceItem.getId(), BigDecimal.ZERO);
        BigDecimal remaining = invoiceItem.getQuantity().subtract(alreadyCredited).subtract(claimedSoFar);

        if (requestedQty.compareTo(remaining) > 0) {
            throw new BusinessException(
                    "'" + invoiceItem.getProductNameSnapshot() + "' has only " + remaining.stripTrailingZeros().toPlainString()
                            + " " + invoiceItem.getUnit() + " left to return on invoice " + invoice.getInvoiceNumber());
        }
        claimedInThisRequest.put(invoiceItem.getId(), claimedSoFar.add(requestedQty));

        BigDecimal effectiveUnitRate = BigDecimal.valueOf(invoiceItem.getLineSubtotalPaise())
                .divide(invoiceItem.getQuantity(), 6, RoundingMode.HALF_UP);
        long unitPricePaise = effectiveUnitRate.setScale(0, RoundingMode.HALF_UP).longValueExact();
        long lineSubtotalPaise = effectiveUnitRate.multiply(requestedQty)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        long lineGstPaise = BigDecimal.valueOf(lineSubtotalPaise)
                .multiply(invoiceItem.getGstRatePercent())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();

        return CreditNoteItem.builder()
                .invoiceItem(invoiceItem)
                .product(invoiceItem.getProduct())
                .productNameSnapshot(invoiceItem.getProductNameSnapshot())
                .quantity(requestedQty)
                .unit(invoiceItem.getUnit())
                .unitPricePaise(unitPricePaise)
                .gstRatePercent(invoiceItem.getGstRatePercent())
                .lineSubtotalPaise(lineSubtotalPaise)
                .lineGstPaise(lineGstPaise)
                .lineTotalPaise(lineSubtotalPaise + lineGstPaise)
                .build();
    }

    private String nextCreditNoteNumber(Long tenantId) {
        return documentSequenceService.next(DocumentType.CREDIT_NOTE, tenantId);
    }

    private CreditNote require(Long id, Long tenantId) {
        return creditNoteRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Credit note", id));
    }
}
