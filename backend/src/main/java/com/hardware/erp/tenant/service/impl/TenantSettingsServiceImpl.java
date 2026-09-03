package com.hardware.erp.tenant.service.impl;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.dto.TenantSettingsRequest;
import com.hardware.erp.tenant.dto.TenantSettingsResponse;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantLogoRepository;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.repository.TenantSignatureRepository;
import com.hardware.erp.tenant.repository.TenantUpiQrRepository;
import com.hardware.erp.tenant.service.TenantSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every tenant-scoped module reads this data (invoice/quotation PDF GST
 * block, CGST/SGST-vs-IGST split) so it lives here rather than under a
 * generic "settings" package - there is exactly one row to manage, the
 * caller's own tenant, never addressed by id from the client (CR-016).
 */
@Service
@RequiredArgsConstructor
public class TenantSettingsServiceImpl implements TenantSettingsService {

    private static final String MODULE = "SETTINGS";
    private static final String ENTITY = "TENANT";

    private final TenantRepository tenantRepository;
    private final TenantLogoRepository logoRepository;
    private final TenantSignatureRepository signatureRepository;
    private final TenantUpiQrRepository upiQrRepository;
    private final ActivityLogService activityLog;
    private final com.hardware.erp.tenant.service.SubscriptionService subscriptionService;
    private final com.hardware.erp.billing.service.RazorpayConfigResolver razorpayConfigResolver;

    @Override
    @Transactional(readOnly = true)
    public TenantSettingsResponse get() {
        // Runs first, in its own REQUIRES_NEW transaction that commits
        // before returning - if a CR-032 trial has expired, this is what
        // reverts it. Called before currentTenant() below (not after) so
        // that read, in this method's own transaction, sees the committed
        // revert rather than returning a Tenant this transaction already
        // has cached from before the revert happened.
        subscriptionService.currentTier();
        return toResponse(currentTenant());
    }

    @Override
    @Transactional
    public TenantSettingsResponse update(TenantSettingsRequest request) {
        Tenant tenant = currentTenant();

        Map<String, Object> before = snapshot(tenant);

        tenant.setName(request.name().trim());
        tenant.setGstNo(blankToNull(request.gstNo()));
        tenant.setAddressLine1(blankToNull(request.addressLine1()));
        tenant.setAddressLine2(blankToNull(request.addressLine2()));
        tenant.setCity(blankToNull(request.city()));
        tenant.setStateCode(blankToNull(request.stateCode()));
        tenant.setPincode(blankToNull(request.pincode()));
        tenant.setSignatoryName(blankToNull(request.signatoryName()));
        tenant.setPanNo(blankToNull(request.panNo()));
        tenant.setPhone(blankToNull(request.phone()));
        tenant.setEmail(blankToNull(request.email()));
        tenant.setBankAccountName(blankToNull(request.bankAccountName()));
        tenant.setBankAccountNo(blankToNull(request.bankAccountNo()));
        tenant.setBankIfsc(blankToNull(request.bankIfsc()));
        tenant.setBankName(blankToNull(request.bankName()));
        tenant.setUpiId(blankToNull(request.upiId()));
        if (request.subscriptionTier() != null) {
            // CR-057 phase 9 gap, found while wiring real Razorpay billing:
            // this self-declared picker (CR-027) was originally the ONLY way
            // to change plan, because no gateway existed. Once one is
            // actually configured, silently granting a paid tier here would
            // be a free bypass of checkout. A downgrade (including to FREE)
            // stays fully self-service in both cases - it never needs a
            // payment. When billing is not configured, behaviour is
            // unchanged from before this phase.
            if (razorpayConfigResolver.resolve().active()
                    && request.subscriptionTier().ordinal() > tenant.getSubscriptionTier().ordinal()) {
                throw new com.hardware.erp.common.exception.BusinessException(
                        "Upgrading to a paid plan needs checkout - use Billing > Upgrade plan.",
                        org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "UPGRADE_REQUIRES_CHECKOUT");
            }
            tenant.setSubscriptionTier(request.subscriptionTier());
            // A manual pick from the dropdown is always permanent (CR-027's
            // original behaviour) - it must clear any trial coupon (CR-032)
            // in progress, or the trial's expiry would later revert a tier
            // the owner explicitly chose back to FREE behind their back.
            tenant.setSubscriptionTrialExpiresAt(null);
        }
        if (request.invoiceTheme() != null) {
            tenant.setInvoiceTheme(request.invoiceTheme());
        }
        tenant.setShowItemDescription(request.showItemDescription());
        tenant.setShowAlternateUnit(request.showAlternateUnit());
        tenant.setShowPriceHistory(request.showPriceHistory());
        tenant.setEnableFreeQuantity(request.enableFreeQuantity());
        tenant.setShowInvoiceTime(request.showInvoiceTime());
        tenant.setShowItemImage(request.showItemImage());
        tenant.setInvoiceTagline(blankToNull(request.invoiceTagline()));
        tenant.setTdsEnabled(request.tdsEnabled());
        tenant.setTdsSectionCode(blankToNull(request.tdsSectionCode()));
        tenant.setTdsRatePercent(request.tdsRatePercent() == null ? java.math.BigDecimal.ZERO : request.tdsRatePercent());
        tenant.setTcsEnabled(request.tcsEnabled());
        tenant.setTcsSectionCode(blankToNull(request.tcsSectionCode()));
        tenant.setTcsRatePercent(request.tcsRatePercent() == null ? java.math.BigDecimal.ZERO : request.tcsRatePercent());
        tenant.setEinvoiceEnabled(request.einvoiceEnabled());
        tenant.setPaymentDueReminderEnabled(request.paymentDueReminderEnabled());
        tenant.setLowStockAlertEnabled(request.lowStockAlertEnabled());

        Tenant saved = tenantRepository.save(tenant);

        activityLog.updated(MODULE, ENTITY, saved.getId(), saved.getName(), before, snapshot(saved));

        return toResponse(saved);
    }

    private Tenant currentTenant() {
        return tenantRepository.getReferenceById(SecurityUtils.requireCurrentTenantId());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static Map<String, Object> snapshot(Tenant tenant) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("gstNo", tenant.getGstNo());
        values.put("addressLine1", tenant.getAddressLine1());
        values.put("addressLine2", tenant.getAddressLine2());
        values.put("city", tenant.getCity());
        values.put("stateCode", tenant.getStateCode());
        values.put("pincode", tenant.getPincode());
        values.put("signatoryName", tenant.getSignatoryName());
        values.put("panNo", tenant.getPanNo());
        values.put("phone", tenant.getPhone());
        values.put("email", tenant.getEmail());
        values.put("bankAccountName", tenant.getBankAccountName());
        values.put("bankAccountNo", tenant.getBankAccountNo());
        values.put("bankIfsc", tenant.getBankIfsc());
        values.put("bankName", tenant.getBankName());
        values.put("upiId", tenant.getUpiId());
        values.put("subscriptionTier", tenant.getSubscriptionTier());
        values.put("invoiceTheme", tenant.getInvoiceTheme());
        values.put("showItemDescription", tenant.isShowItemDescription());
        values.put("showAlternateUnit", tenant.isShowAlternateUnit());
        values.put("showPriceHistory", tenant.isShowPriceHistory());
        values.put("enableFreeQuantity", tenant.isEnableFreeQuantity());
        values.put("showInvoiceTime", tenant.isShowInvoiceTime());
        values.put("showItemImage", tenant.isShowItemImage());
        values.put("invoiceTagline", tenant.getInvoiceTagline());
        values.put("tdsEnabled", tenant.isTdsEnabled());
        values.put("tdsSectionCode", tenant.getTdsSectionCode());
        values.put("tdsRatePercent", tenant.getTdsRatePercent());
        values.put("tcsEnabled", tenant.isTcsEnabled());
        values.put("tcsSectionCode", tenant.getTcsSectionCode());
        values.put("tcsRatePercent", tenant.getTcsRatePercent());
        values.put("einvoiceEnabled", tenant.isEinvoiceEnabled());
        values.put("paymentDueReminderEnabled", tenant.isPaymentDueReminderEnabled());
        values.put("lowStockAlertEnabled", tenant.isLowStockAlertEnabled());
        return values;
    }

    private TenantSettingsResponse toResponse(Tenant tenant) {
        return new TenantSettingsResponse(
                tenant.getId(), tenant.getName(), tenant.getGstNo(),
                tenant.getAddressLine1(), tenant.getAddressLine2(), tenant.getCity(),
                tenant.getStateCode(), tenant.getPincode(), tenant.getSignatoryName(),
                logoRepository.existsById(tenant.getId()),
                signatureRepository.existsById(tenant.getId()),
                upiQrRepository.existsById(tenant.getId()),
                tenant.getPanNo(), tenant.getPhone(), tenant.getEmail(),
                tenant.getBankAccountName(), tenant.getBankAccountNo(), tenant.getBankIfsc(),
                tenant.getBankName(), tenant.getUpiId(), tenant.getSubscriptionTier(),
                tenant.getSubscriptionTrialExpiresAt(), tenant.getInvoiceTheme(),
                tenant.isShowItemDescription(), tenant.isShowAlternateUnit(), tenant.isShowPriceHistory(),
                tenant.isEnableFreeQuantity(), tenant.isShowInvoiceTime(), tenant.isShowItemImage(),
                tenant.getInvoiceTagline(),
                tenant.isTdsEnabled(), tenant.getTdsSectionCode(), tenant.getTdsRatePercent(),
                tenant.isTcsEnabled(), tenant.getTcsSectionCode(), tenant.getTcsRatePercent(),
                tenant.isEinvoiceEnabled(),
                tenant.isPaymentDueReminderEnabled(), tenant.isLowStockAlertEnabled());
    }
}
