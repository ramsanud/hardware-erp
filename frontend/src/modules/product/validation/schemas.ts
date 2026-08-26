import { z } from 'zod';

/**
 * Mirrors the backend Bean Validation constraints on CategoryRequest,
 * BrandRequest and ProductRequest exactly.
 *
 * This exists for immediate feedback only. The backend re-validates every
 * field and is the authority.
 */

// ^$|^[A-Z0-9][A-Z0-9-]{1,29}$
const codeRules = (label: string) =>
  z
    .string()
    .trim()
    .toUpperCase()
    .max(30, `${label} must be 30 characters or fewer`)
    .regex(/^$|^[A-Z0-9][A-Z0-9-]{1,29}$/, `${label} may contain uppercase letters, digits and hyphens`)
    .optional()
    .or(z.literal(''));

export const categorySchema = z.object({
  categoryCode: codeRules('Category code'),
  categoryName: z.string().trim().min(1, 'Category name is required').max(150),
  parentCategoryId: z.coerce.number().int().positive().optional().nullable(),
  description: z.string().trim().max(255).optional().or(z.literal('')),
  status: z.enum(['ACTIVE', 'INACTIVE']),
});

export const brandSchema = z.object({
  brandCode: codeRules('Brand code'),
  brandName: z.string().trim().min(1, 'Brand name is required').max(150),
  description: z.string().trim().max(255).optional().or(z.literal('')),
  status: z.enum(['ACTIVE', 'INACTIVE']),
});

export const productSchema = z.object({
  productCode: codeRules('Product code'),
  productName: z.string().trim().min(1, 'Product name is required').max(255),
  categoryId: z.coerce.number().int().positive().optional().nullable(),
  brandId: z.coerce.number().int().positive().optional().nullable(),
  modelNo: z.string().trim().max(60).optional().or(z.literal('')),
  manufacturerCode: z.string().trim().max(60).optional().or(z.literal('')),
  barcode: z.string().trim().max(60).optional().or(z.literal('')),
  unit: z.string().trim().min(1, 'Unit is required').max(20),
  description: z.string().trim().optional().or(z.literal('')),
  hsnCode: z.string().trim().max(10).optional().or(z.literal('')),
  gstRatePercent: z.coerce
    .number()
    .min(0, 'GST rate cannot be negative')
    .max(100, 'GST rate cannot exceed 100'),
  // Entered in rupees; converted to *Paise before the request is sent.
  purchasePriceRupees: z.coerce.number().min(0, 'Purchase price cannot be negative'),
  sellingPriceRupees: z.coerce.number().min(0, 'Selling price cannot be negative'),
  mrpRupees: z.coerce.number().min(0, 'MRP cannot be negative'),
  minimumStock: z.coerce.number().min(0, 'Minimum stock cannot be negative'),
  reorderLevel: z.coerce.number().min(0, 'Reorder level cannot be negative'),
  status: z.enum(['ACTIVE', 'INACTIVE']),
});

export type CategoryValues = z.infer<typeof categorySchema>;
export type BrandValues = z.infer<typeof brandSchema>;
export type ProductValues = z.infer<typeof productSchema>;
