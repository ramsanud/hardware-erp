export type PurchaseStatus = 'DRAFT' | 'RECEIVED' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';

export type PaymentMethod = 'CASH' | 'UPI' | 'CARD' | 'BANK_TRANSFER' | 'OTHER';

export interface PurchaseItemRequest {
  productId: number;
  quantity: number;
  unitPricePaise: number;
  gstRatePercent: number;
}

export interface PurchaseRequest {
  supplierId: number;
  supplierBillNumber?: string | null;
  purchaseDate: string;
  items: PurchaseItemRequest[];
  updateProductCost: boolean;
  initialPaymentPaise?: number | null;
  paymentMethod?: PaymentMethod | null;
  remarks?: string | null;
}

export interface PurchaseItemResponse {
  id: number;
  productId: number;
  productName: string;
  quantity: number;
  unit: string;
  unitPriceDisplay: string;
  gstRatePercent: string;
  lineSubtotalDisplay: string;
  lineGstDisplay: string;
  lineTotalDisplay: string;
}

export interface PurchasePaymentResponse {
  id: number;
  amountDisplay: string;
  paymentMethod: PaymentMethod;
  paymentDate: string;
  notes?: string | null;
}

export interface PurchaseResponse {
  id: number;
  purchaseNumber: string;
  supplierId: number;
  supplierName: string;
  supplierMobile: string;
  supplierBillNumber?: string | null;
  purchaseDate: string;
  subtotalDisplay: string;
  gstAmountDisplay: string;
  totalDisplay: string;
  paidDisplay: string;
  balanceDisplay: string;
  status: PurchaseStatus;
  remarks?: string | null;
  imported: boolean;
  importedAt?: string | null;
  items: PurchaseItemResponse[];
  payments: PurchasePaymentResponse[];
  hasDocument: boolean;
  createdAt: string;
}

export interface PurchaseSummaryResponse {
  id: number;
  purchaseNumber: string;
  supplierName: string;
  supplierBillNumber?: string | null;
  purchaseDate: string;
  totalDisplay: string;
  balanceDisplay: string;
  status: PurchaseStatus;
  imported: boolean;
}

export interface RecordPurchasePaymentRequest {
  amountPaise: number;
  paymentMethod: PaymentMethod;
  notes?: string | null;
}

// ---- Supplier Bill Import ----

export interface ImportRowPreview {
  rowNumber: number;
  productName: string | null;
  brandName: string | null;
  categoryName: string | null;
  sku: string | null;
  quantity: number | null;
  unit: string | null;
  unitPricePaise: number | null;
  gstRatePercent: number;
  lineTotalPaise: number | null;
  productIsExisting: boolean;
  matchedProductId: number | null;
  matchedProductName: string | null;
  matchedProductCurrentStock: number | null;
  matchedProductCurrentPurchasePricePaise: number | null;
  brandIsExisting: boolean;
  matchedBrandId: number | null;
  categoryIsExisting: boolean;
  matchedCategoryId: number | null;
  errors: string[];
}

export interface ImportPreviewResponse {
  extractionAvailable: boolean;
  message: string | null;
  rows: ImportRowPreview[];
  warnings: string[];
  totalRows: number;
  rowsWithErrors: number;
  newProductCount: number;
  existingProductCount: number;
}

export interface ImportConfirmRow {
  rowNumber: number;
  existingProductId: number | null;
  newProductName: string | null;
  newProductSku: string | null;
  newProductCategoryId: number | null;
  newProductBrandId: number | null;
  newProductUnit: string | null;
  quantity: number;
  unitPricePaise: number;
  gstRatePercent: number;
  updateExistingProductCost: boolean;
}

export interface ImportConfirmRequest {
  supplierId: number;
  supplierBillNumber?: string | null;
  purchaseDate: string;
  rows: ImportConfirmRow[];
  confirmDuplicateAnyway: boolean;
}

export interface ImportResultResponse {
  purchaseId: number;
  purchaseNumber: string;
  rowsImported: number;
  existingProductsMatched: number;
  newProductsCreated: number;
  rowsMergedWithEarlierRow: number;
  stockAddedDisplay: string;
  totalPurchaseDisplay: string;
}
