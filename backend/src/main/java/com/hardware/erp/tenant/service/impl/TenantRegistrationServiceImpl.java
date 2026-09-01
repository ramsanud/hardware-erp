package com.hardware.erp.tenant.service.impl;

import com.hardware.erp.auth.entity.Permission;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.auth.repository.PermissionRepository;
import com.hardware.erp.auth.repository.RoleRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.legal.LegalDocumentVersions;
import com.hardware.erp.legal.entity.ConsentType;
import com.hardware.erp.legal.entity.UserConsent;
import com.hardware.erp.legal.repository.UserConsentRepository;
import org.springframework.http.HttpStatus;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.dto.TenantRegistrationResponse;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.TenantRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provisions a brand-new shop: tenant + its own OWNER/MANAGER/ACCOUNTANT/STAFF
 * roles + the signing-up owner account, atomically. This is the "real tenant
 * provisioning" flow CR-016 explicitly deferred - BootstrapOwnerInitializer
 * remains as the *first* tenant's one-time bootstrap (env-var driven, for
 * the operator standing up the whole platform), this is what every
 * *subsequent* shop goes through.
 *
 * The default role/permission grants mirror V1__auth_schema.sql's seed data
 * exactly (OWNER = every permission, the other three as hand-picked lists) -
 * kept here as a literal map rather than copied from another tenant's
 * current (possibly customized) roles at runtime, so a new shop always gets
 * the canonical starting point regardless of what any other tenant has done
 * to their own roles since.
 */
@Service
@RequiredArgsConstructor
public class TenantRegistrationServiceImpl implements TenantRegistrationService {

    private static final Map<String, String> ROLE_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> ROLE_DESCRIPTIONS = new LinkedHashMap<>();

    /**
     * permission.module_code for permissions that are developer tooling rather
     * than an ERP capability. Excluded from OWNER's otherwise-everything grant
     * below - see CR-045 and V30__developer_inspection_permission.sql.
     */
    static final String DEVELOPER_MODULE = "DEVELOPER";

    /**
     * Package-private, not private, so RoleGrantDriftTest can assert every
     * permission code is deliberately either granted or withheld here. This
     * map is a second source of truth for the default grants (the migrations
     * are the first), and it silently drifted once already - a new shop got
     * MANAGER/ACCOUNTANT roles with no labour access at all after V25 added
     * those codes. See BUG-LAB-006.
     */
    static final Map<String, Set<String>> ROLE_PERMISSIONS = new LinkedHashMap<>();

    static {
        ROLE_NAMES.put("OWNER", "Owner");
        ROLE_NAMES.put("MANAGER", "Manager");
        ROLE_NAMES.put("ACCOUNTANT", "Accountant");
        ROLE_NAMES.put("STAFF", "Staff");

        ROLE_DESCRIPTIONS.put("OWNER", "Unrestricted access");
        ROLE_DESCRIPTIONS.put("MANAGER", "Day to day operations, sees cost");
        ROLE_DESCRIPTIONS.put("ACCOUNTANT", "Billing, payments and financial reports");
        ROLE_DESCRIPTIONS.put("STAFF", "Billing counter, no cost visibility");

        // OWNER gets every permission that exists - assigned separately in
        // createRole() using the live permission table, not this map, so a
        // future new permission is automatically included for OWNER without
        // this class needing an edit.
        ROLE_PERMISSIONS.put("MANAGER", Set.of(
                "USER_VIEW", "ROLE_VIEW",
                "CUSTOMER_VIEW", "CUSTOMER_MANAGE", "SUPPLIER_VIEW", "SUPPLIER_MANAGE",
                "PRODUCT_VIEW", "PRODUCT_MANAGE", "PRODUCT_VIEW_COST", "PRODUCT_VIEW_STOCK",
                "PURCHASE_VIEW", "PURCHASE_MANAGE",
                "QUOTATION_VIEW", "QUOTATION_MANAGE", "INVOICE_VIEW", "INVOICE_CREATE",
                "INVOICE_DISCOUNT_OVERRIDE",
                "INVENTORY_VIEW", "INVENTORY_ADJUST",
                "PAYMENT_VIEW", "PAYMENT_MANAGE", "EXPENSE_VIEW", "EXPENSE_MANAGE",
                "REPORT_VIEW", "SETTINGS_VIEW",
                "COUPON_VIEW", "COUPON_MANAGE",
                "PROJECT_VIEW", "PROJECT_MANAGE", "PROJECT_MATERIAL_VIEW", "PROJECT_MATERIAL_MANAGE",
                "LABOUR_VIEW", "LABOUR_MANAGE",
                "SALES_ORDER_VIEW", "SALES_ORDER_MANAGE",
                "DELIVERY_CHALLAN_VIEW", "DELIVERY_CHALLAN_MANAGE",
                "CREDIT_NOTE_VIEW", "CREDIT_NOTE_MANAGE"));
        ROLE_PERMISSIONS.put("ACCOUNTANT", Set.of(
                "CUSTOMER_VIEW", "CUSTOMER_MANAGE", "SUPPLIER_VIEW",
                "PRODUCT_VIEW", "PRODUCT_VIEW_COST", "PRODUCT_VIEW_STOCK",
                "QUOTATION_VIEW", "INVOICE_VIEW", "INVOICE_CREATE",
                "PAYMENT_VIEW", "PAYMENT_MANAGE", "EXPENSE_VIEW", "EXPENSE_MANAGE",
                "PURCHASE_VIEW", "INVENTORY_VIEW",
                "REPORT_VIEW", "REPORT_FINANCIAL",
                "COUPON_VIEW",
                "PROJECT_VIEW", "PROJECT_MATERIAL_VIEW",
                "LABOUR_VIEW", "LABOUR_MANAGE",
                // Sees orders/challans for billing context but does not raise
                // them - same reasoning as QUOTATION_VIEW without MANAGE.
                "SALES_ORDER_VIEW", "DELIVERY_CHALLAN_VIEW",
                // A credit note is a financial document, same footing as
                // INVOICE_CREATE.
                "CREDIT_NOTE_VIEW", "CREDIT_NOTE_MANAGE"));
        // STAFF deliberately excludes PRODUCT_VIEW_COST - counter staff must
        // not see purchase cost or margin, enforced server-side (see V1's
        // identical comment on the seed data this mirrors).
        ROLE_PERMISSIONS.put("STAFF", Set.of(
                "CUSTOMER_VIEW", "CUSTOMER_MANAGE",
                "PRODUCT_VIEW", "PRODUCT_VIEW_STOCK",
                "QUOTATION_VIEW", "QUOTATION_MANAGE",
                "INVOICE_VIEW", "INVOICE_CREATE",
                "PAYMENT_VIEW", "INVENTORY_VIEW",
                "COUPON_VIEW",
                "PROJECT_VIEW",
                // Counter staff takes orders the same way it raises
                // quotations and invoices.
                "SALES_ORDER_VIEW", "SALES_ORDER_MANAGE"));
    }

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserConsentRepository userConsentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public TenantRegistrationResponse register(TenantRegistrationRequest request) {
        // Global uniqueness by design (CR-016) - login has no tenant
        // selector, so an identifier must resolve to exactly one user
        // platform-wide, checked here up front for a clean error rather
        // than a database constraint violation.
        if (userRepository.existsByMobileNo(request.mobileNo().trim())) {
            throw new DuplicateResourceException("Mobile number", request.mobileNo());
        }
        if (userRepository.existsByEmailIgnoreCase(request.email().trim())) {
            throw new DuplicateResourceException("Email", request.email());
        }

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .slug(uniqueSlug(request.shopName()))
                .name(request.shopName().trim())
                .status(TenantStatus.ACTIVE)
                .subscriptionTier(request.subscriptionTier() != null ? request.subscriptionTier() : SubscriptionTier.FREE)
                .build());

        // "OWNER gets every permission that exists" - except the DEVELOPER
        // module, which is not an ERP capability (CR-045). Without this
        // filter, adding DEVELOPER_INSPECT to the catalogue would silently
        // hand a diagnostics console to the owner of every shop registered
        // afterwards, which is the "admin = developer" conflation CR-045
        // exists to prevent. V30 makes the same exclusion for the roles that
        // already existed; DeveloperInspectionModuleGrantTest asserts both.
        Set<String> ownerPermissionCodes = permissionRepository.findAllByOrderByModuleCodeAscDisplayOrderAsc()
                .stream()
                .filter(permission -> !DEVELOPER_MODULE.equals(permission.getModuleCode()))
                .map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Role ownerRole = createRole(tenant, "OWNER", ownerPermissionCodes);
        createRole(tenant, "MANAGER", ROLE_PERMISSIONS.get("MANAGER"));
        createRole(tenant, "ACCOUNTANT", ROLE_PERMISSIONS.get("ACCOUNTANT"));
        createRole(tenant, "STAFF", ROLE_PERMISSIONS.get("STAFF"));

        User owner = userRepository.save(User.builder()
                .tenant(tenant)
                .role(ownerRole)
                .employeeCode("EMP001")
                .fullName(request.ownerFullName().trim())
                .mobileNo(request.mobileNo().trim())
                .email(request.email().trim().toLowerCase(Locale.ROOT))
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                // Unlike bootstrap/admin-created accounts, the owner chose
                // this password themselves at signup - no forced reset.
                .mustChangePassword(false)
                .tokenVersion(0)
                .failedLoginAttempts(0)
                .passwordChangedAt(LocalDateTime.now())
                .build());

        recordConsent(tenant, owner, ConsentType.TERMS, request.termsVersion(), true);
        recordConsent(tenant, owner, ConsentType.PRIVACY, request.privacyVersion(), true);
        // Recorded either way. A row saying "declined at this time" is the
        // only way to distinguish a deliberate opt-out from an account that
        // predates the question being asked.
        recordConsent(tenant, owner, ConsentType.MARKETING, null,
                Boolean.TRUE.equals(request.marketingConsent()));

        return new TenantRegistrationResponse(tenant.getId(), tenant.getSlug(), tenant.getName(), owner.getMobileNo());
    }

    /**
     * Writes one consent row, rejecting a version the server never published.
     *
     * Trusting the submitted string would let a client record agreement to a
     * version that does not exist, or silently to an older one - either makes
     * the record useless as evidence. TERMS/PRIVACY are checked; MARKETING has
     * no document and so carries no version.
     */
    private void recordConsent(Tenant tenant, User owner, ConsentType type, String submittedVersion, boolean granted) {
        String version = null;
        if (type != ConsentType.MARKETING) {
            String current = LegalDocumentVersions.currentFor(type);
            if (submittedVersion != null && !submittedVersion.isBlank()
                    && !current.equals(submittedVersion.trim())) {
                throw new BusinessException(
                        "The %s you agreed to is out of date. Please reload the page and review the current version."
                                .formatted(type == ConsentType.TERMS ? "Terms & Conditions" : "Privacy Policy"),
                        HttpStatus.CONFLICT, "LEGAL_DOCUMENT_VERSION_MISMATCH");
            }
            // A client that sends no version is recorded against the version
            // that was current when the account was created, which is the
            // document it must have been shown.
            version = current;
        }

        userConsentRepository.save(UserConsent.builder()
                .tenant(tenant)
                .user(owner)
                .consentType(type)
                .documentVersion(version)
                .granted(granted)
                .recordedAt(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSlugAvailable(String slug) {
        String normalized = normalizeSlug(slug);
        return !normalized.isBlank() && !tenantRepository.existsBySlug(normalized);
    }

    private Role createRole(Tenant tenant, String code, Set<String> permissionCodes) {
        Set<Permission> permissions = new LinkedHashSet<>(permissionRepository.findByCodeIn(permissionCodes));
        return roleRepository.save(Role.builder()
                .tenant(tenant)
                .code(code)
                .name(ROLE_NAMES.get(code))
                .description(ROLE_DESCRIPTIONS.get(code))
                .systemRole(true)
                .status(RoleStatus.ACTIVE)
                .permissions(permissions)
                .build());
    }

    private String uniqueSlug(String shopName) {
        String base = normalizeSlug(shopName);
        if (base.isBlank()) {
            base = "shop";
        }
        String candidate = base;
        int suffix = 1;
        while (tenantRepository.existsBySlug(candidate)) {
            suffix++;
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private String normalizeSlug(String value) {
        if (value == null) {
            return "";
        }
        String slug = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.length() <= 60 ? slug : slug.substring(0, 60);
    }
}
