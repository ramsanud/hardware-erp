package com.hardware.erp.branch.service;

import com.hardware.erp.branch.entity.Branch;
import com.hardware.erp.branch.repository.BranchRepository;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * CR-092. Answers "which branch is this action for" - the acting user's
 * own branch, else the shop's MAIN branch. Always server-side from the
 * JWT user, never from a request body: a counter user cannot bill against
 * another branch by editing a payload, for exactly the reason a tenant id
 * is never accepted from the client.
 *
 * Kept tiny and dependency-free (one repository) because the inventory,
 * invoice and purchase services all call it on their hot paths.
 */
@Component
@RequiredArgsConstructor
public class BranchContext {

    private final BranchRepository branchRepository;

    public Long actingBranchId(Long tenantId) {
        Long userBranch = SecurityUtils.currentUser().map(u -> u.getBranchId()).orElse(null);
        if (userBranch != null) {
            return userBranch;
        }
        return mainBranchId(tenantId);
    }

    public Long mainBranchId(Long tenantId) {
        return branchRepository.findByTenantIdAndMainTrue(tenantId)
                .map(Branch::getId)
                .orElseThrow(() -> new BusinessException(
                        "This shop has no main branch - contact support.",
                        HttpStatus.INTERNAL_SERVER_ERROR, "NO_MAIN_BRANCH"));
    }

    /** True while the shop has only its MAIN branch - the state every shop is in until it adds one. */
    public boolean singleBranch(Long tenantId) {
        return branchRepository.countByTenantId(tenantId) <= 1;
    }
}
