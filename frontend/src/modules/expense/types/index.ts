import type { PaymentMethod } from '@/modules/invoice/types';

export type ExpenseStatus = 'ACTIVE' | 'CANCELLED';

export interface ExpenseCategoryRequest {
  name: string;
  description?: string | null;
}

export interface ExpenseCategoryResponse {
  id: number;
  name: string;
  description?: string | null;
}

export interface BusinessExpenseRequest {
  expenseDate: string;
  categoryId: number;
  amountPaise: number;
  paymentMethod: PaymentMethod;
  notes?: string | null;
}

export interface BusinessExpenseResponse {
  id: number;
  expenseDate: string;
  categoryId: number;
  categoryName: string;
  amountPaise: number;
  amountDisplay: string;
  paymentMethod: PaymentMethod;
  notes?: string | null;
  status: ExpenseStatus;
  hasReceipt: boolean;
  createdAt: string;
}

export interface ExpenseTotalResponse {
  totalAmountPaise: number;
  totalAmountDisplay: string;
}

export interface ExpenseSearchParams {
  search?: string;
  status?: ExpenseStatus;
  categoryId?: number;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}
