/** Mirrors backend discovery/dto/DiscoveryDtos.java exactly (CR-090). */

export type DiscoveryAvailability = 'AVAILABLE' | 'LIKELY_AVAILABLE';
export type OwnerNotificationType = 'PRODUCT_DISCOVERY' | 'DAILY_SUMMARY' | 'LOW_STOCK' | 'SYSTEM';

export interface DiscoverySettingRequest {
  discoveryEnabled: boolean;
  shareShopName: boolean;
  sharePhone: boolean;
  shareApproximateLocation: boolean;
  shareAvailability: boolean;
  latitude: number | null;
  longitude: number | null;
  searchRadiusKm: number;
}

export interface DiscoverySettingResponse extends DiscoverySettingRequest {
  updatedAt?: string | null;
}

/** Every optional field is absent because THAT shop chose not to share it. */
export interface NearbyShopResponse {
  matchId: number;
  shopName?: string | null;
  phone?: string | null;
  whatsappUrl?: string | null;
  distanceKm?: number | null;
  matchedProductName: string;
  availability: DiscoveryAvailability;
  foundAt: string;
}

export interface NearbyAvailabilityResponse {
  productRequestId: number;
  requestedProductName: string;
  searchUnavailable: boolean;
  searchUnavailableReason?: string | null;
  shops: NearbyShopResponse[];
}

export interface OwnerNotificationResponse {
  id: number;
  notificationType: OwnerNotificationType;
  title: string;
  body: string;
  referenceType?: string | null;
  referenceId?: number | null;
  readAt?: string | null;
  createdAt: string;
}
