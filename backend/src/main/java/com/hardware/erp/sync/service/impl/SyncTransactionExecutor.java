package com.hardware.erp.sync.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.invoice.dto.InvoiceRequest;
import com.hardware.erp.invoice.dto.InvoiceResponse;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.sync.dto.SyncDtos.SyncTransactionRequest;
import com.hardware.erp.sync.dto.SyncDtos.SyncTransactionResult;
import com.hardware.erp.sync.entity.SyncTransaction;
import com.hardware.erp.sync.entity.SyncTransactionStatus;
import com.hardware.erp.sync.repository.SyncTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * CR-091 Phase 9. Deliberately NOT itself wrapped in one enclosing
 * transaction - see {@link SyncInvoiceCreator}'s javadoc for the
 * UnexpectedRollbackException that shape caused (found by
 * OfflineSyncIT.insufficientStockAtSyncTimeIsAConflictNotASilentRetry):
 * InvoiceServiceImpl.create() is plain {@code @Transactional}, so calling
 * it from a transaction this method itself held let its failure mark that
 * shared transaction rollback-only, and catching the exception here could
 * not undo that - the save() below still failed at commit. The invoice
 * attempt now runs inside SyncInvoiceCreator's own REQUIRES_NEW
 * transaction instead; each repository call below (find, save) opens its
 * own short transaction when none is active, so a failed attempt still
 * leaves a clean transaction to record the outcome in.
 *
 * "Do not silently overwrite server data" (brief) is honoured by
 * construction: InvoiceServiceImpl.create() has never accepted a
 * client-supplied price - it always prices from the product row as it
 * stands right now - so there is no server value this could overwrite
 * with a stale offline one. What CAN genuinely conflict is stock a
 * walk-in customer took while this device was offline; that surfaces as
 * InvoiceService's own INSUFFICIENT_STOCK BusinessException and is
 * recorded as CONFLICT here, never silently retried against less stock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncTransactionExecutor {

    private final SyncTransactionRepository repository;
    private final SyncInvoiceCreator syncInvoiceCreator;
    private final ObjectMapper objectMapper;

    public SyncTransactionResult processOne(SyncTransactionRequest transaction) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();

        var existing = repository.findByTenantIdAndClientUuid(tenantId, transaction.clientUuid());
        if (existing.isPresent()) {
            return toResult(existing.get(), true);
        }

        SyncTransaction record = new SyncTransaction();
        record.setTenantId(tenantId);
        record.setClientUuid(transaction.clientUuid());
        record.setDeviceId(transaction.deviceId());
        record.setTransactionType(transaction.transactionType());
        record.setPayload(transaction.payload());
        record.setClientCreatedAt(transaction.clientCreatedAt());
        record.setReceivedAt(LocalDateTime.now());
        record.setCreatedBy(SecurityUtils.currentUserId().orElse(null));

        try {
            InvoiceRequest invoiceRequest = objectMapper.convertValue(transaction.payload(), InvoiceRequest.class);
            InvoiceResponse created = syncInvoiceCreator.create(invoiceRequest);
            record.setStatus(SyncTransactionStatus.SYNCED);
            record.setResultReferenceType("INVOICE");
            record.setResultReferenceId(created.id());
            record.setResultReferenceNumber(created.invoiceNumber());
            record.setSyncedAt(LocalDateTime.now());
        } catch (BusinessException businessException) {
            // A business rule the server enforces did not hold at sync time -
            // most commonly stock someone else sold while this device was
            // offline. Recorded, not silently retried or overwritten.
            record.setStatus(SyncTransactionStatus.CONFLICT);
            record.setConflictReason(businessException.getMessage());
        } catch (Exception unexpected) {
            log.error("Offline sync transaction {} failed", transaction.clientUuid(), unexpected);
            record.setStatus(SyncTransactionStatus.FAILED);
            record.setConflictReason("Unexpected error - please retry or contact support.");
        }

        try {
            SyncTransaction saved = repository.save(record);
            return toResult(saved, false);
        } catch (DataIntegrityViolationException raceLostToAnotherUpload) {
            // Two uploads of the same UUID landed within the same instant -
            // the other one won the unique constraint. Its row is now the
            // truth; hand that back rather than a duplicate-key error.
            SyncTransaction winner = repository.findByTenantIdAndClientUuid(tenantId, transaction.clientUuid())
                    .orElseThrow(() -> raceLostToAnotherUpload);
            return toResult(winner, true);
        }
    }

    private SyncTransactionResult toResult(SyncTransaction record, boolean replay) {
        return new SyncTransactionResult(record.getClientUuid(), record.getStatus(), replay,
                record.getResultReferenceType(), record.getResultReferenceId(), record.getResultReferenceNumber(),
                record.getConflictReason(), null);
    }
}
