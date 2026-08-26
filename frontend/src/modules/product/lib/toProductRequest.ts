import type { ProductRequest } from '../types';
import type { ProductValues } from '../validation/schemas';

function blankToNull(value?: string): string | null {
  return value ? value : null;
}

/**
 * Converts the form's schema shape into the wire DTO. The only real
 * transformation is money: the form collects rupees for a human to read;
 * the backend stores paise (CLAUDE.md money rule), same pattern as
 * supplier/lib/toSupplierRequest.ts.
 */
export function toProductRequest(values: ProductValues): ProductRequest {
  return {
    productCode: values.productCode || undefined,
    productName: values.productName,
    categoryId: values.categoryId ?? null,
    brandId: values.brandId ?? null,
    modelNo: blankToNull(values.modelNo),
    manufacturerCode: blankToNull(values.manufacturerCode),
    barcode: blankToNull(values.barcode),
    unit: values.unit,
    description: blankToNull(values.description),
    hsnCode: blankToNull(values.hsnCode),
    gstRatePercent: values.gstRatePercent,
    purchasePricePaise: Math.round(values.purchasePriceRupees * 100),
    sellingPricePaise: Math.round(values.sellingPriceRupees * 100),
    mrpPaise: Math.round(values.mrpRupees * 100),
    minimumStock: values.minimumStock,
    reorderLevel: values.reorderLevel,
    status: values.status,
  };
}
