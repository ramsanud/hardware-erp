package com.hardware.erp.tenant.entity;

import com.hardware.erp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * One row per shop (CR-016). Every tenant-owned table - app_user, role,
 * supplier and everything Modules 3-12 add - carries tenant_id and every
 * query against it is scoped from the authenticated caller's own tenant,
 * never from client input. See CR-016 in CHANGE_REQUEST_REGISTRY.md.
 */
@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_id")
    private Long id;

    /** Lowercase, unique. Reserved for subdomain-based tenant resolution if that is ever added. */
    @Column(name = "slug", nullable = false, length = 60, unique = true)
    private String slug;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

    /** All nullable (CR-022): a shop that hasn't filled in Settings yet still trades. */
    @Column(name = "gst_no", length = 15)
    private String gstNo;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "pincode", length = 6)
    private String pincode;

    /** Printed on the GST bill as "For {name}" + this + "Authorized Signatory" - not a PKI signature, see CR-022. */
    @Column(name = "signatory_name", length = 100)
    private String signatoryName;

    @Column(name = "pan_no", length = 10)
    private String panNo;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "bank_account_name", length = 200)
    private String bankAccountName;

    @Column(name = "bank_account_no", length = 30)
    private String bankAccountNo;

    @Column(name = "bank_ifsc", length = 11)
    private String bankIfsc;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    /** VPA for the invoice PDF's UPI QR code, e.g. "shopname@okicici". */
    @Column(name = "upi_id", length = 100)
    private String upiId;

    /** Self-declared (CR-027) - no payment gateway is wired in, so this is a feature-gating flag the owner sets, not a billed subscription. */
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, length = 10)
    @Builder.Default
    private SubscriptionTier subscriptionTier = SubscriptionTier.FREE;

    /**
     * CR-032 - set only when the current subscriptionTier came from redeeming
     * a SubscriptionCoupon trial. Null means the tier (whatever it is) is
     * permanent, exactly the original CR-027 picker behaviour. Checked
     * lazily on the next SubscriptionServiceImpl.currentTier() call, not by
     * a scheduled job - once passed, the tier reverts to FREE right there.
     */
    @Column(name = "subscription_trial_expires_at")
    private java.time.LocalDateTime subscriptionTrialExpiresAt;

    /** CR-053. Shop-wide default skin for the generated invoice PDF - see InvoicePdfService. */
    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_theme", nullable = false, length = 20)
    @Builder.Default
    private InvoiceTheme invoiceTheme = InvoiceTheme.CLASSIC;

    // ---------------------------------------------------------------
    // CR-053 backlog item 1: invoice "Additional Settings" toggles
    // (myBillBook parity). All default false/null - see V40's migration
    // comment for why that keeps every existing tenant's PDF unchanged.
    // ---------------------------------------------------------------

    @Column(name = "show_item_description", nullable = false)
    @Builder.Default
    private boolean showItemDescription = false;

    @Column(name = "show_alternate_unit", nullable = false)
    @Builder.Default
    private boolean showAlternateUnit = false;

    /** Gates the Price History section on the Product Detail page - not an invoice PDF toggle. */
    @Column(name = "show_price_history", nullable = false)
    @Builder.Default
    private boolean showPriceHistory = false;

    /** Gates whether the "Free Qty" field appears at all on invoice line entry - avoids clutter for shops that never give free units. */
    @Column(name = "enable_free_quantity", nullable = false)
    @Builder.Default
    private boolean enableFreeQuantity = false;

    @Column(name = "show_invoice_time", nullable = false)
    @Builder.Default
    private boolean showInvoiceTime = false;

    @Column(name = "show_item_image", nullable = false)
    @Builder.Default
    private boolean showItemImage = false;

    /** Presence is the toggle - null/blank prints nothing, same convention as signatoryName. */
    @Column(name = "invoice_tagline", length = 255)
    private String invoiceTagline;

    // ---------------------------------------------------------------
    // CR-053 backlog item 3: TDS/TCS settings. Informational only - see
    // V41's migration comment for why this never touches a document's own
    // stored totalPaise/balancePaise.
    // ---------------------------------------------------------------

    @Column(name = "tds_enabled", nullable = false)
    @Builder.Default
    private boolean tdsEnabled = false;

    @Column(name = "tds_section_code", length = 20)
    private String tdsSectionCode;

    @Column(name = "tds_rate_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private java.math.BigDecimal tdsRatePercent = java.math.BigDecimal.ZERO;

    @Column(name = "tcs_enabled", nullable = false)
    @Builder.Default
    private boolean tcsEnabled = false;

    @Column(name = "tcs_section_code", length = 20)
    private String tcsSectionCode;

    @Column(name = "tcs_rate_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private java.math.BigDecimal tcsRatePercent = java.math.BigDecimal.ZERO;

    /**
     * CR-053 backlog item 4. Shows the e-Invoice (IRN) review section on
     * the Invoice detail page - generation itself always stays disabled,
     * see V42's migration comment for why no IRN/acknowledgement columns
     * exist anywhere in this schema.
     */
    @Column(name = "einvoice_enabled", nullable = false)
    @Builder.Default
    private boolean einvoiceEnabled = false;

    /**
     * CR-053 backlog item 5. Read once a day by ReminderSchedulerService -
     * see its own javadoc for exactly what each toggle triggers.
     */
    @Column(name = "payment_due_reminder_enabled", nullable = false)
    @Builder.Default
    private boolean paymentDueReminderEnabled = false;

    @Column(name = "low_stock_alert_enabled", nullable = false)
    @Builder.Default
    private boolean lowStockAlertEnabled = false;

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }
}
