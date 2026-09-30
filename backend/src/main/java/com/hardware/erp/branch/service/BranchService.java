package com.hardware.erp.branch.service;

import com.hardware.erp.branch.dto.BranchDtos.AssignUserBranchRequest;
import com.hardware.erp.branch.dto.BranchDtos.BranchRequest;
import com.hardware.erp.branch.dto.BranchDtos.BranchResponse;
import com.hardware.erp.branch.dto.BranchDtos.BranchStockResponse;
import com.hardware.erp.branch.dto.BranchDtos.BranchSummaryResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * CR-092. Listing branches is free (every shop has its MAIN one and the
 * name is printed on documents); creating a second one, transfers and the
 * per-branch views are gated on FeatureKey.MULTI_BRANCH inside the service,
 * so an internal caller is refused exactly like an HTTP one.
 */
public interface BranchService {

    List<BranchResponse> list();

    BranchResponse get(Long id);

    BranchResponse create(BranchRequest request);

    BranchResponse update(Long id, BranchRequest request);

    List<BranchSummaryResponse> summary(LocalDate from, LocalDate to);

    List<BranchStockResponse> stock(Long branchId, String search);

    BranchResponse assignUser(Long userId, AssignUserBranchRequest request);
}
