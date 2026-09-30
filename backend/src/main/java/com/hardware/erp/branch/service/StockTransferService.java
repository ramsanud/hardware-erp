package com.hardware.erp.branch.service;

import com.hardware.erp.branch.dto.BranchDtos.StockTransferRequest;
import com.hardware.erp.branch.dto.BranchDtos.StockTransferResponse;
import com.hardware.erp.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

/** CR-092. Branch-to-branch stock transfers - PREMIUM (MULTI_BRANCH), STOCK_TRANSFER_MANAGE. */
public interface StockTransferService {

    StockTransferResponse create(StockTransferRequest request);

    PageResponse<StockTransferResponse> list(Pageable pageable);

    StockTransferResponse get(Long id);
}
