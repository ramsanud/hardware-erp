import type { CouponRequest } from '../types';
import type { CouponValues } from '../validation/schemas';

/** 0 in the form means "not set" - the backend represents that as null (no restriction/cap/limit). */
function rupeesToPaiseOrNull(rupees: number): number | null {
  return rupees > 0 ? Math.round(rupees * 100) : null;
}

/**
 * Converts the form's schema shape into the wire DTO. The only real
 * transformation is money: the form collects rupees for a human to read;
 * the backend stores paise (CLAUDE.md money rule), same pattern as
 * product/lib/toProductRequest.ts.
 */
export function toCouponRequest(values: CouponValues): CouponRequest {
  return {
    code: values.code,
    description: values.description || null,
    discountType: values.discountType,
    discountValue: values.discountValue,
    minPurchasePaise: rupeesToPaiseOrNull(values.minPurchaseRupees),
    maxDiscountPaise: rupeesToPaiseOrNull(values.maxDiscountRupees),
    validFrom: values.validFrom || null,
    validUntil: values.validUntil || null,
    usageLimit: values.usageLimit > 0 ? Math.round(values.usageLimit) : null,
    status: values.status,
    productIds: values.productIds && values.productIds.length > 0 ? values.productIds : null,
  };
}
