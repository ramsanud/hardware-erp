package com.hardware.erp.tenant.service.impl;

import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.config.DeploymentProperties;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.supplier.entity.SupplierStatus;
import com.hardware.erp.supplier.repository.SupplierRepository;
import com.hardware.erp.tenant.dto.UsageSummaryResponse;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.service.EntitlementService;
import com.hardware.erp.tenant.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntitlementServiceImpl implements EntitlementService {

    private final DeploymentProperties deployment;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public void requireCanAddOwner() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SubscriptionTier tier = subscriptionService.currentTier();
        require(tier.maxOwners(), userRepository.countActiveOwners(tenantId), "owner", tier);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireCanAddCustomer() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SubscriptionTier tier = subscriptionService.currentTier();
        require(tier.maxCustomers(),
                customerRepository.countByStatusAndTenantId(CustomerStatus.ACTIVE, tenantId), "customer", tier);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireCanAddSupplier() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SubscriptionTier tier = subscriptionService.currentTier();
        require(tier.maxSuppliers(),
                supplierRepository.countByStatusAndTenantId(SupplierStatus.ACTIVE, tenantId), "supplier", tier);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireCanAddProduct() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SubscriptionTier tier = subscriptionService.currentTier();
        require(tier.maxProducts(),
                productRepository.countByStatusAndTenantId(ProductStatus.ACTIVE, tenantId), "product", tier);
    }

    @Override
    @Transactional(readOnly = true)
    public UsageSummaryResponse usageSummary() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        SubscriptionTier tier = subscriptionService.currentTier();

        // CR-059. Report the caps that are actually enforced, not the ones the
        // tier nominally carries - a self-hosted install would otherwise show
        // "142 / 200 customers" and an amber warning bar for a ceiling that
        // require() never applies. The UI already renders UNLIMITED (-1) as
        // "Unlimited" with no progress bar, so this needs no frontend change.
        boolean capped = deployment.billingApplies();
        return new UsageSummaryResponse(
                tier,
                userRepository.countActiveOwners(tenantId), cap(capped, tier.maxOwners()),
                customerRepository.countByStatusAndTenantId(CustomerStatus.ACTIVE, tenantId), cap(capped, tier.maxCustomers()),
                supplierRepository.countByStatusAndTenantId(SupplierStatus.ACTIVE, tenantId), cap(capped, tier.maxSuppliers()),
                productRepository.countByStatusAndTenantId(ProductStatus.ACTIVE, tenantId), cap(capped, tier.maxProducts()));
    }

    private int cap(boolean capped, int tierLimit) {
        return capped ? tierLimit : SubscriptionTier.UNLIMITED;
    }

    private void require(int limit, long currentCount, String noun, SubscriptionTier tier) {
        // CR-059. Subscription tiers are a property of the hosted product. On a
        // self-hosted install the client has bought the software outright,
        // there is no checkout to reach, and the FREE-tier default would
        // otherwise stop them adding their 51st customer with a message
        // telling them to "upgrade the plan in Shop Settings" - a dead end
        // that would make the product unusable for exactly the customer who
        // paid the most for it.
        if (!deployment.billingApplies()) {
            return;
        }
        if (limit == SubscriptionTier.UNLIMITED) {
            return;
        }
        if (currentCount >= limit) {
            throw new BusinessException(
                    "Your " + tier.displayName() + " plan allows up to " + limit + " " + noun + "s. "
                            + "Upgrade the plan in Shop Settings to add more.",
                    HttpStatus.PAYMENT_REQUIRED, "ENTITLEMENT_LIMIT_REACHED");
        }
    }
}
