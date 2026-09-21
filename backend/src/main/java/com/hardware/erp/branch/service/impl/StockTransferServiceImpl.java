package com.hardware.erp.branch.service.impl;

import com.hardware.erp.branch.dto.BranchDtos.StockTransferItemRequest;
import com.hardware.erp.branch.dto.BranchDtos.StockTransferItemResponse;
import com.hardware.erp.branch.dto.BranchDtos.StockTransferRequest;
import com.hardware.erp.branch.dto.BranchDtos.StockTransferResponse;
import com.hardware.erp.branch.entity.Branch;
import com.hardware.erp.branch.entity.BranchStatus;
import com.hardware.erp.branch.entity.StockTransfer;
import com.hardware.erp.branch.entity.StockTransferItem;
import com.hardware.erp.branch.repository.BranchRepository;
import com.hardware.erp.branch.repository.StockTransferRepository;
import com.hardware.erp.branch.service.StockTransferService;
import com.hardware.erp.common.activity.ActivityAction;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.subscription.entity.FeatureKey;
import com.hardware.erp.subscription.service.FeatureAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockTransferServiceImpl implements StockTransferService {

    private final StockTransferRepository transferRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final DocumentSequenceService documentSequenceService;
    private final FeatureAccessService featureAccessService;
    private final ActivityLogService activityLog;

    @Override
    @Transactional
    public StockTransferResponse create(StockTransferRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        featureAccessService.requireFeature(FeatureKey.MULTI_BRANCH);
        if (request.fromBranchId().equals(request.toBranchId())) {
            throw new BusinessException("Choose two different branches", HttpStatus.UNPROCESSABLE_ENTITY, "SAME_BRANCH");
        }
        Branch from = activeBranch(request.fromBranchId(), tenantId);
        Branch to = activeBranch(request.toBranchId(), tenantId);

        // Two lines for the same product would race each other's branch check;
        // merge them so the check sees the whole quantity at once.
        Map<Long, BigDecimal> merged = new LinkedHashMap<>();
        for (StockTransferItemRequest item : request.items()) {
            merged.merge(item.productId(), item.quantity(), BigDecimal::add);
        }

        StockTransfer transfer = StockTransfer.builder()
                .tenantId(tenantId)
                .transferNumber(documentSequenceService.next(DocumentType.STOCK_TRANSFER, tenantId))
                .fromBranchId(from.getId())
                .toBranchId(to.getId())
                .notes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim())
                .build();
        for (Map.Entry<Long, BigDecimal> line : merged.entrySet()) {
            Product product = productRepository.findByIdAndTenantId(line.getKey(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", line.getKey()));
            transfer.getItems().add(StockTransferItem.builder()
                    .transfer(transfer)
                    .productId(product.getId())
                    .productNameSnapshot(product.getProductName())
                    .quantity(line.getValue())
                    .build());
        }
        StockTransfer saved = transferRepository.save(transfer);

        for (StockTransferItem item : saved.getItems()) {
            stockService.applyBranchTransfer(item.getProductId(), item.getQuantity(),
                    from.getId(), to.getId(), saved.getId(), saved.getTransferNumber());
        }

        activityLog.action("BRANCH", "STOCK_TRANSFER", saved.getId(), saved.getTransferNumber(),
                ActivityAction.CREATE, from.getBranchName() + " -> " + to.getBranchName()
                        + ", " + saved.getItems().size() + " line(s)");
        return toResponse(saved, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockTransferResponse> list(Pageable pageable) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return PageResponse.from(transferRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable),
                t -> toResponse(t, branch(t.getFromBranchId()), branch(t.getToBranchId())));
    }

    @Override
    @Transactional(readOnly = true)
    public StockTransferResponse get(Long id) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        StockTransfer t = transferRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock transfer", id));
        return toResponse(t, branch(t.getFromBranchId()), branch(t.getToBranchId()));
    }

    private Branch activeBranch(Long id, Long tenantId) {
        Branch branch = branchRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw new BusinessException("Branch " + branch.getBranchName() + " is inactive",
                    HttpStatus.UNPROCESSABLE_ENTITY, "BRANCH_INACTIVE");
        }
        return branch;
    }

    private Branch branch(Long id) {
        return branchRepository.findById(id).orElse(null);
    }

    private StockTransferResponse toResponse(StockTransfer t, Branch from, Branch to) {
        return new StockTransferResponse(t.getId(), t.getTransferNumber(),
                t.getFromBranchId(), from == null ? null : from.getBranchName(),
                t.getToBranchId(), to == null ? null : to.getBranchName(),
                t.getStatus(), t.getNotes(),
                t.getItems().stream()
                        .map(i -> new StockTransferItemResponse(i.getProductId(), i.getProductNameSnapshot(), i.getQuantity()))
                        .toList(),
                t.getCreatedAt());
    }
}
