export interface StockResponse {
  productId: number;
  productCode: string;
  productName: string;
  unit: string;
  quantityOnHand: number;
  reorderLevel: number;
  lowStock: boolean;
}

export interface StockSearchParams {
  search?: string;
  lowStockOnly?: boolean;
  page?: number;
  size?: number;
}

export interface StockAdjustmentRequest {
  quantityChange: number;
  notes?: string | null;
}
