/**
 * Mirrors backend coupon/dto/*.java exactly.
 * Changing a name here without changing the DTO breaks at runtime, not compile
 * time, so these must be kept in step with the backend records.
 */

export type DiscountType = 'PERCENT' | 'FLAT';
export type CouponStatus = 'ACTIVE' | 'INACTIVE';

export interface CouponRequest {
  code: string;
  description?: string | null;
  discountType: DiscountType;
  discountValue: number;
  /** Paise. Null means no minimum purchase requirement. */
  minPurchasePaise?: number | null;
  /** Paise. Null means no cap on the discount amount. */
  maxDiscountPaise?: number | null;
  validFrom?: string | null;
  validUntil?: string | null;
  /** Null means unlimited uses. */
  usageLimit?: number | null;
  status: CouponStatus;
  /** Empty/null = every product is eligible. */
  productIds?: number[] | null;
}

export interface CouponProductRef {
  id: number;
  productCode: string;
  productName: string;
}

export interface CouponResponse {
  id: number;
  code: string;
  description?: string | null;
  discountType: DiscountType;
  discountValue: number;
  minPurchasePaise?: number | null;
  minPurchaseDisplay?: string | null;
  maxDiscountPaise?: number | null;
  maxDiscountDisplay?: string | null;
  validFrom?: string | null;
  validUntil?: string | null;
  usageLimit?: number | null;
  timesUsed: number;
  status: CouponStatus;
  currentlyValid: boolean;
  products: CouponProductRef[];
}

/** The list-screen projection. */
export interface CouponSummaryResponse {
  id: number;
  code: string;
  discountType: DiscountType;
  discountValue: number;
  usageLimit?: number | null;
  timesUsed: number;
  status: CouponStatus;
  currentlyValid: boolean;
  restrictedToProducts: boolean;
}

// ---- query params ----

export interface CouponSearchParams {
  search?: string;
  status?: CouponStatus;
  page?: number;
  size?: number;
}
