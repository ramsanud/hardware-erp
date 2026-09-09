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
  /**
   * CR-070. A different question from lowStockOnly: low is a purchasing
   * prompt, zero is a lost sale in progress. The two AND server-side, so
   * sending both is coherent rather than contradictory.
   */
  outOfStockOnly?: boolean;
  page?: number;
  size?: number;
}

export interface StockAdjustmentRequest {
  quantityChange: number;
  notes?: string | null;
}
