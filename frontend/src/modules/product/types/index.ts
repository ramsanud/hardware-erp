/**
 * Mirrors backend product/dto/*.java exactly.
 * Changing a name here without changing the DTO breaks at runtime, not compile
 * time, so these must be kept in step with the backend records.
 */

export type CategoryStatus = 'ACTIVE' | 'INACTIVE';
export type BrandStatus = 'ACTIVE' | 'INACTIVE';
export type ProductStatus = 'ACTIVE' | 'INACTIVE';

// ---- category ----

export interface CategoryRequest {
  categoryCode?: string;
  categoryName: string;
  parentCategoryId?: number | null;
  description?: string | null;
  status: CategoryStatus;
}

export interface CategoryResponse {
  id: number;
  categoryCode: string;
  categoryName: string;
  parentCategoryId?: number | null;
  parentCategoryName?: string | null;
  description?: string | null;
  status: CategoryStatus;
  productCount: number;
}

// ---- brand ----

export interface BrandRequest {
  brandCode?: string;
  brandName: string;
  description?: string | null;
  status: BrandStatus;
}

export interface BrandResponse {
  id: number;
  brandCode: string;
  brandName: string;
  description?: string | null;
  status: BrandStatus;
  productCount: number;
}

// ---- product ----

export interface ProductRequest {
  productCode?: string;
  productName: string;
  categoryId?: number | null;
  brandId?: number | null;
  modelNo?: string | null;
  manufacturerCode?: string | null;
  barcode?: string | null;
  unit: string;
  description?: string | null;
  hsnCode?: string | null;
  gstRatePercent: number;
  /** Paise. */
  purchasePricePaise: number;
  /** Paise. */
  sellingPricePaise: number;
  /** Paise. 0 means not applicable. */
  mrpPaise: number;
  minimumStock: number;
  reorderLevel: number;
  status: ProductStatus;

  /** CR-053 backlog item 1. Both set together or both left blank - see the form's own validation. */
  altUnitLabel?: string | null;
  altUnitConversionFactor?: number | null;
}

export interface ProductResponse {
  id: number;
  productCode: string;
  productName: string;

  categoryId?: number | null;
  categoryName?: string | null;
  brandId?: number | null;
  brandName?: string | null;

  modelNo?: string | null;
  manufacturerCode?: string | null;
  barcode?: string | null;
  unit: string;
  description?: string | null;
  hsnCode?: string | null;
  gstRatePercent: number;

  /** Null unless the caller holds PRODUCT_VIEW_COST. */
  purchasePricePaise?: number | null;
  /** Null unless the caller holds PRODUCT_VIEW_COST. */
  purchasePriceDisplay?: string | null;

  sellingPricePaise: number;
  sellingPriceDisplay: string;
  mrpPaise: number;
  mrpDisplay: string;

  minimumStock: number;
  reorderLevel: number;

  status: ProductStatus;

  createdAt?: string | null;
  updatedAt?: string | null;
  hasImage: boolean;

  /** CR-053 backlog item 1. */
  altUnitLabel?: string | null;
  altUnitConversionFactor?: number | null;
}

/** CR-053 backlog item 1 - one row per past invoice line for this product, newest first. */
export interface ProductPriceHistoryResponse {
  invoiceDate: string;
  invoiceNumber: string;
  customerName: string;
  quantity: number;
  unitPriceDisplay: string;
}

/** The list-screen projection. Purchase price is omitted unconditionally - see SupplierSummaryResponse. */
export interface ProductSummaryResponse {
  id: number;
  productCode: string;
  productName: string;
  categoryName?: string | null;
  brandName?: string | null;
  unit: string;
  sellingPriceDisplay: string;
  gstRatePercent: string;
  status: ProductStatus;
  hasImage: boolean;
}

// ---- bulk import (CR-036) ----

export interface ProductImportRowPreview {
  rowNumber: number;
  productName: string | null;
  productCode: string | null;
  categoryName: string | null;
  matchedCategoryId: number | null;
  brandName: string | null;
  matchedBrandId: number | null;
  unit: string | null;
  hsnCode: string | null;
  gstRatePercent: number | null;
  purchasePriceRupees: number | null;
  sellingPriceRupees: number | null;
  mrpRupees: number | null;
  minimumStock: number | null;
  reorderLevel: number | null;
  errors: string[];
}

export interface ProductImportPreviewResponse {
  extractionAvailable: boolean;
  message: string | null;
  rows: ProductImportRowPreview[];
  warnings: string[];
  totalRows: number;
  rowsWithErrors: number;
}

export interface ProductImportConfirmRow {
  rowNumber: number;
  productName: string;
  productCode: string | null;
  categoryId: number | null;
  brandId: number | null;
  unit: string;
  hsnCode: string | null;
  gstRatePercent: number;
  purchasePriceRupees: number;
  sellingPriceRupees: number;
  mrpRupees: number;
  minimumStock: number | null;
  reorderLevel: number | null;
}

export interface ProductImportConfirmRequest {
  rows: ProductImportConfirmRow[];
}

export interface ProductImportResultResponse {
  productsCreated: number;
}

// ---- query params ----

export interface ProductSearchParams {
  search?: string;
  status?: ProductStatus;
  categoryId?: number;
  brandId?: number;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}
