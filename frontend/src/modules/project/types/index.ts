import type { PaymentMethod } from '@/modules/invoice/types';

export type ProjectStatus = 'UPCOMING' | 'IN_PROGRESS' | 'ON_HOLD' | 'CANCELLED' | 'COMPLETED';
export type ProjectOutcome = 'SUCCESS' | 'FAILURE';
export type ProjectExpenseCategory = 'LABOUR' | 'EMPLOYEE' | 'FOOD' | 'STAY' | 'PETROL' | 'OTHER';

export interface WorkTypeRequest {
  name: string;
  description?: string | null;
}

export interface WorkTypeResponse {
  id: number;
  name: string;
  description?: string | null;
}

export interface ProjectRequest {
  projectName: string;
  customerId: number;
  workTypeId: number;
  description?: string | null;
  siteAddress?: string | null;
  startDate?: string | null;
  expectedCompletionDate?: string | null;
  customerDeadline?: string | null;
  projectValuePaise: number;
  managerUserId?: number | null;
  notes?: string | null;
}

export interface ProjectStatusChangeRequest {
  status: ProjectStatus;
  outcome?: ProjectOutcome | null;
}

export interface ProjectResponse {
  id: number;
  projectNumber: string;
  projectName: string;
  customerId: number;
  customerName: string;
  workTypeId: number;
  workTypeName: string;
  description?: string | null;
  siteAddress?: string | null;
  startDate?: string | null;
  expectedCompletionDate?: string | null;
  actualCompletionDate?: string | null;
  customerDeadline?: string | null;
  status: ProjectStatus;
  outcome?: ProjectOutcome | null;
  overdue: boolean;
  projectValueDisplay: string;
  totalMaterialCostDisplay: string;
  totalExpenseCostDisplay: string;
  totalCostDisplay: string;
  netProfitDisplay: string;
  profitPositive: boolean;
  profitMarginPercentDisplay: string;
  totalReceivedDisplay: string;
  balanceReceivableDisplay: string;
  /** Live-computed from worker attendance x rate (CR-036 phase 4) - not folded into totalCostDisplay/netProfitDisplay. */
  totalLabourCostDisplay: string;
  managerUserId?: number | null;
  managerUserName?: string | null;
  notes?: string | null;
  createdAt: string;
}

export interface ProjectSummaryResponse {
  id: number;
  projectNumber: string;
  projectName: string;
  customerName: string;
  workTypeName: string;
  status: ProjectStatus;
  outcome?: ProjectOutcome | null;
  overdue: boolean;
  projectValueDisplay: string;
  netProfitDisplay: string;
  profitPositive: boolean;
}

export interface ProjectSearchParams {
  search?: string;
  status?: ProjectStatus;
  customerId?: number;
  page?: number;
  size?: number;
}

export interface ProjectMaterialRequest {
  productId: number;
  supplierId?: number | null;
  quantityRequired?: number | null;
  quantityEstimated?: number | null;
  quantityActual?: number | null;
  quantityWastage?: number | null;
  unitPricePaise?: number | null;
  notes?: string | null;
}

export interface ProjectMaterialResponse {
  id: number;
  productId: number;
  productName: string;
  productCode: string;
  supplierId?: number | null;
  supplierName?: string | null;
  quantityRequired?: number | null;
  quantityEstimated?: number | null;
  quantityActual?: number | null;
  quantityWastage?: number | null;
  unit: string;
  unitPriceDisplay: string;
  totalCostDisplay: string;
  notes?: string | null;
  createdAt: string;
}

export interface ProjectExpenseRequest {
  category: ProjectExpenseCategory;
  amountPaise: number;
  expenseDate: string;
  paidTo?: string | null;
  description?: string | null;
}

export interface ProjectExpenseResponse {
  id: number;
  category: ProjectExpenseCategory;
  amountDisplay: string;
  expenseDate: string;
  paidTo?: string | null;
  description?: string | null;
}

export interface ProjectPaymentRequest {
  amountPaise: number;
  paymentMethod: PaymentMethod;
  paymentDate: string;
  notes?: string | null;
}

export interface ProjectPaymentResponse {
  id: number;
  amountDisplay: string;
  paymentMethod: PaymentMethod;
  paymentDate: string;
  notes?: string | null;
}

export interface RooftopCalculatorRequest {
  widthMeters: number;
  lengthMeters: number;
  sheetWidthMeters: number;
  sheetLengthMeters: number;
  overlapPercent?: number | null;
  wastagePercent?: number | null;
}

export interface RooftopCalculatorResponse {
  requiredAreaSqMeters: number;
  areaAfterOverlapAndWastageSqMeters: number;
  sheetAreaSqMeters: number;
  calculatedSheetQuantity: number;
}
