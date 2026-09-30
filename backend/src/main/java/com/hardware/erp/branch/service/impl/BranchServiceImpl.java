package com.hardware.erp.branch.service.impl;

import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.branch.dto.BranchDtos.AssignUserBranchRequest;
import com.hardware.erp.branch.dto.BranchDtos.BranchRequest;
import com.hardware.erp.branch.dto.BranchDtos.BranchResponse;
import com.hardware.erp.branch.dto.BranchDtos.BranchStockResponse;
import com.hardware.erp.branch.dto.BranchDtos.BranchSummaryResponse;
import com.hardware.erp.branch.entity.Branch;
import com.hardware.erp.branch.entity.BranchStatus;
import com.hardware.erp.branch.repository.BranchRepository;
import com.hardware.erp.branch.repository.BranchStockRepository;
import com.hardware.erp.branch.repository.BranchSummaryRepository;
import com.hardware.erp.branch.service.BranchService;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.subscription.entity.FeatureKey;
import com.hardware.erp.subscription.service.FeatureAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private static final String MODULE = "BRANCH";

    private final BranchRepository branchRepository;
    private final BranchSummaryRepository summaryRepository;
    private final BranchStockRepository branchStockRepository;
    private final UserRepository userRepository;
    private final FeatureAccessService featureAccessService;
    private final ActivityLogService activityLog;

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> list() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return branchRepository.findByTenantIdOrderByMainDescBranchNameAsc(tenantId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse get(Long id) {
        return toResponse(find(id, SecurityUtils.requireCurrentTenantId()));
    }

    @Override
    @Transactional
    public BranchResponse create(BranchRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        featureAccessService.requireFeature(FeatureKey.MULTI_BRANCH);
        String code = request.branchCode().trim().toUpperCase();
        if (branchRepository.existsByTenantIdAndBranchCodeIgnoreCase(tenantId, code)) {
            throw new BusinessException("Branch code '" + code + "' is already in use", HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
        }
        if (branchRepository.countByTenantId(tenantId) == 1) {
            // Going from one branch to two: everything the shop holds is at
            // MAIN. Record that now, before an absent row starts meaning zero.
            Long mainId = branchRepository.findByTenantIdAndMainTrue(tenantId).map(Branch::getId).orElseThrow();
            branchStockRepository.snapshotMainFromShopStock(tenantId, mainId);
        }
        Branch branch = Branch.builder()
                .tenantId(tenantId)
                .branchCode(code)
                .branchName(request.branchName().trim())
                .addressLine1(blankToNull(request.addressLine1()))
                .city(blankToNull(request.city()))
                .stateCode(blankToNull(request.stateCode()))
                .pincode(blankToNull(request.pincode()))
                .phone(blankToNull(request.phone()))
                .main(false)
                .status(request.status() == null ? BranchStatus.ACTIVE : request.status())
                .build();
        Branch saved = branchRepository.save(branch);
        activityLog.created(MODULE, "BRANCH", saved.getId(), saved.getBranchName(), snapshot(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public BranchResponse update(Long id, BranchRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Branch branch = find(id, tenantId);
        Map<String, Object> before = snapshot(branch);
        String code = request.branchCode().trim().toUpperCase();
        if (!code.equalsIgnoreCase(branch.getBranchCode())
                && branchRepository.existsByTenantIdAndBranchCodeIgnoreCase(tenantId, code)) {
            throw new BusinessException("Branch code '" + code + "' is already in use", HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
        }
        if (branch.isMain() && request.status() == BranchStatus.INACTIVE) {
            throw new BusinessException("The main branch cannot be made inactive", HttpStatus.UNPROCESSABLE_ENTITY, "MAIN_BRANCH_REQUIRED");
        }
        branch.setBranchCode(code);
        branch.setBranchName(request.branchName().trim());
        branch.setAddressLine1(blankToNull(request.addressLine1()));
        branch.setCity(blankToNull(request.city()));
        branch.setStateCode(blankToNull(request.stateCode()));
        branch.setPincode(blankToNull(request.pincode()));
        branch.setPhone(blankToNull(request.phone()));
        if (request.status() != null) {
            branch.setStatus(request.status());
        }
        Branch saved = branchRepository.save(branch);
        activityLog.updated(MODULE, "BRANCH", saved.getId(), saved.getBranchName(), before, snapshot(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchSummaryResponse> summary(LocalDate from, LocalDate to) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        featureAccessService.requireFeature(FeatureKey.MULTI_BRANCH);
        return summaryRepository.summary(tenantId, from, to).stream()
                .map(r -> new BranchSummaryResponse(r.getBranchId(), r.getBranchCode(), r.getBranchName(),
                        Boolean.TRUE.equals(r.getMain()),
                        n(r.getInvoiceCount()), n(r.getSalesPaise()), IndianCurrencyFormat.rupees(n(r.getSalesPaise())),
                        n(r.getPurchaseCount()), n(r.getPurchasesPaise()), IndianCurrencyFormat.rupees(n(r.getPurchasesPaise())),
                        n(r.getUserCount()), n(r.getProductsInStock())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchStockResponse> stock(Long branchId, String search) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        featureAccessService.requireFeature(FeatureKey.MULTI_BRANCH);
        if (branchId != null) {
            find(branchId, tenantId);
        }
        String term = search == null || search.isBlank() ? null : search.trim();
        return branchStockRepository.breakdown(tenantId, branchId, term).stream()
                .map(r -> new BranchStockResponse(r.getBranchId(), r.getBranchName(), r.getProductId(),
                        r.getProductCode(), r.getProductName(), r.getUnit(), r.getQuantityOnHand()))
                .toList();
    }

    @Override
    @Transactional
    public BranchResponse assignUser(Long userId, AssignUserBranchRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        featureAccessService.requireFeature(FeatureKey.MULTI_BRANCH);
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Branch branch = request.branchId() == null ? null : find(request.branchId(), tenantId);
        Long before = user.getBranchId();
        user.setBranchId(branch == null ? null : branch.getId());
        userRepository.save(user);
        activityLog.updated(MODULE, "USER_BRANCH", user.getId(), user.getFullName(),
                Map.of("branchId", before == null ? "all" : before),
                Map.of("branchId", branch == null ? "all" : branch.getId()));
        return branch == null ? null : toResponse(branch);
    }

    private Branch find(Long id, Long tenantId) {
        return branchRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));
    }

    private BranchResponse toResponse(Branch b) {
        return new BranchResponse(b.getId(), b.getBranchCode(), b.getBranchName(), b.getAddressLine1(), b.getCity(),
                b.getStateCode(), b.getPincode(), b.getPhone(), b.isMain(), b.getStatus(), b.getCreatedAt());
    }

    private static Map<String, Object> snapshot(Branch b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("branchCode", b.getBranchCode());
        m.put("branchName", b.getBranchName());
        m.put("addressLine1", b.getAddressLine1());
        m.put("city", b.getCity());
        m.put("stateCode", b.getStateCode());
        m.put("pincode", b.getPincode());
        m.put("phone", b.getPhone());
        m.put("status", b.getStatus().name());
        return m;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long n(Long value) {
        return value == null ? 0L : value;
    }
}
