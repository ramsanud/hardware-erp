/** Backend: branch/dto/BranchDtos.java (CR-092). */

export type BranchStatus = 'ACTIVE' | 'INACTIVE';

export interface BranchRequest {
  branchCode: string;
  branchName: string;
  addressLine1?: string | null;
  city?: string | null;
  stateCode?: string | null;
  pincode?: string | null;
  phone?: string | null;
  status?: BranchStatus | null;
}

export interface BranchResponse {
  id: number;
  branchCode: string;
  branchName: string;
  addressLine1?: string | null;
  city?: string | null;
  stateCode?: string | null;
  pincode?: string | null;
  phone?: string | null;
  main: boolean;
  status: BranchStatus;
  createdAt: string;
}

export interface BranchSummaryResponse {
  branchId: number;
  branchCode: string;
  branchName: string;
  main: boolean;
  invoiceCount: number;
  salesPaise: number;
  salesDisplay: string;
  purchaseCount: number;
  purchasesPaise: number;
  purchasesDisplay: string;
  userCount: number;
  productsInStock: number;
}

export interface BranchStockResponse {
  branchId: number;
  branchName: string;
  productId: number;
  productCode: string;
  productName: string;
  unit: string;
  quantityOnHand: number;
}

export interface StockTransferItemRequest {
  productId: number;
  quantity: number;
}

export interface StockTransferRequest {
  fromBranchId: number;
  toBranchId: number;
  items: StockTransferItemRequest[];
  notes?: string | null;
}

export interface StockTransferItemResponse {
  productId: number;
  productName: string;
  quantity: number;
}

export interface StockTransferResponse {
  id: number;
  transferNumber: string;
  fromBranchId: number;
  fromBranchName?: string | null;
  toBranchId: number;
  toBranchName?: string | null;
  status: string;
  notes?: string | null;
  items: StockTransferItemResponse[];
  createdAt: string;
}
