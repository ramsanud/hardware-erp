package com.hardware.erp.tenant.entity;

import java.util.List;

/**
 * Declaration order is significant - {@link #ordinal()} is used directly by
 * SubscriptionService.requireTier() as a "minimum tier" comparison (FREE <
 * PRO < MAX). Do not reorder without checking that call site.
 *
 * CR-031 (Customer 360 §27-40): each tier also carries entitlement limits -
 * how many active owners/customers/suppliers/products a tenant on that tier
 * may have. UNLIMITED (-1) is MAX's "configurable" limit from the request -
 * there is no billing gateway or platform-admin config screen to make a real
 * per-tenant override meaningful yet, so MAX is simply unlimited rather than
 * inventing config storage nothing else uses.
 */
public enum SubscriptionTier {
    FREE("Free", List.of(
            "Suppliers, Customers, Products", "Quotations & Invoices",
            "Inventory & Payment tracking"),
            1, 100, 100, 1000),
    PRO("Pro", List.of(
            "Everything in Free", "Customer/staff notifications by email, SMS and WhatsApp"),
            2, 1000, 1000, 10000),
    MAX("Max", List.of(
            "Everything in Pro", "AI assistant over your own shop data"),
            -1, -1, -1, -1);

    /** Sentinel meaning "no limit" - never a real count to compare against. */
    public static final int UNLIMITED = -1;

    private final String displayName;
    private final List<String> features;
    private final int maxOwners;
    private final int maxCustomers;
    private final int maxSuppliers;
    private final int maxProducts;

    SubscriptionTier(String displayName, List<String> features,
                      int maxOwners, int maxCustomers, int maxSuppliers, int maxProducts) {
        this.displayName = displayName;
        this.features = features;
        this.maxOwners = maxOwners;
        this.maxCustomers = maxCustomers;
        this.maxSuppliers = maxSuppliers;
        this.maxProducts = maxProducts;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> features() {
        return features;
    }

    public int maxOwners() {
        return maxOwners;
    }

    public int maxCustomers() {
        return maxCustomers;
    }

    public int maxSuppliers() {
        return maxSuppliers;
    }

    public int maxProducts() {
        return maxProducts;
    }
}
