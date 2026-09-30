package com.hardware.erp.discovery.entity;

/**
 * CR-090. The only thing another shop ever learns about this shop's stock.
 * AVAILABLE = on hand covers the requested quantity; LIKELY_AVAILABLE =
 * some on hand, may not cover it. A shop with none on hand is simply not
 * a match - listing it as "Unavailable" would leak catalogue membership
 * for no benefit to the requesting owner.
 */
public enum DiscoveryAvailability {
    AVAILABLE, LIKELY_AVAILABLE
}
