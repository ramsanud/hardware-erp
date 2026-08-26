import { z } from 'zod';

/**
 * Mirrors the backend Bean Validation constraints on CouponRequest exactly.
 *
 * This exists for immediate feedback only. The backend re-validates every
 * field and is the authority.
 */

// ^[A-Z0-9-]+$ - uppercased live, same pattern as product/validation/schemas.ts's codeRules.
const couponCodeSchema = z
  .string()
  .trim()
  .toUpperCase()
  .min(1, 'Coupon code is required')
  .max(30, 'Coupon code must be 30 characters or fewer')
  .regex(/^[A-Z0-9-]+$/, 'Coupon code may contain letters, digits and hyphens');

export const couponSchema = z
  .object({
    code: couponCodeSchema,
    description: z.string().trim().max(255).optional().or(z.literal('')),
    discountType: z.enum(['PERCENT', 'FLAT']),
    discountValue: z.coerce.number().min(0.01, 'Discount value must be greater than 0'),
    // Entered in rupees; converted to *Paise before the request is sent.
    // 0 means "no restriction" and is sent to the backend as null.
    minPurchaseRupees: z.coerce.number().min(0, 'Minimum purchase cannot be negative'),
    maxDiscountRupees: z.coerce.number().min(0, 'Maximum discount cannot be negative'),
    validFrom: z.string().trim().optional().or(z.literal('')),
    validUntil: z.string().trim().optional().or(z.literal('')),
    // 0 means "unlimited" and is sent to the backend as null.
    usageLimit: z.coerce.number().min(0, 'Usage limit cannot be negative'),
    status: z.enum(['ACTIVE', 'INACTIVE']),
    productIds: z.array(z.number()).optional(),
  })
  .refine((values) => values.discountType !== 'PERCENT' || values.discountValue <= 100, {
    message: 'A percent discount cannot exceed 100',
    path: ['discountValue'],
  });

export type CouponValues = z.infer<typeof couponSchema>;
