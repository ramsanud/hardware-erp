package com.hardware.erp.discovery.service;

import com.hardware.erp.discovery.dto.DiscoveryDtos.DiscoverySettingRequest;
import com.hardware.erp.discovery.dto.DiscoveryDtos.DiscoverySettingResponse;
import com.hardware.erp.discovery.dto.DiscoveryDtos.NearbyAvailabilityResponse;

/**
 * CR-090. Owner-side only. Nothing here is ever reachable by a customer;
 * the customer's answer is the same "currently unavailable" it always was.
 */
public interface ShopDiscoveryService {

    DiscoverySettingResponse settings();

    /** Every change is written to activity_log; disabling takes effect on the very next search anyone runs. */
    DiscoverySettingResponse updateSettings(DiscoverySettingRequest request);

    /**
     * Searches opted-in shops within this shop's radius, snapshots the
     * matches on the request, and leaves an owner notification when any
     * were found. Requires the caller's shop to be opted in itself
     * (reciprocity - you search the network you are part of) and to have
     * a location.
     */
    NearbyAvailabilityResponse discover(Long productRequestId);

    NearbyAvailabilityResponse nearby(Long productRequestId);
}
