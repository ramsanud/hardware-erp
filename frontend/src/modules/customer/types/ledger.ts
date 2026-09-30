/** Backend: customer/ledger/LedgerDtos.java (CR-091 Phase 4). Every money figure is server-formatted. */

export type LedgerEntryType = 'INVOICE' | 'PAYMENT' | 'SALES_RETURN' | 'INVOICE_CANCELLATION' | 'ADJUSTMENT';

export interface LedgerBalanceResponse {
  customerId: number;
  customerName: string;
  /** Positive = the customer owes the shop; negative = the customer is in advance. */
  balancePaise: number;
  balanceDisplay: string;
  totalDebitPaise: number;
  totalDebitDisplay: string;
  totalCreditPaise: number;
  totalCreditDisplay: string;
}

export interface LedgerEntryResponse {
  id: number;
  entryType: LedgerEntryType;
  entryDate: string;
  debitPaise: number;
  debitDisplay: string;
  creditPaise: number;
  creditDisplay: string;
  balancePaise: number;
  balanceDisplay: string;
  referenceType?: string | null;
  referenceId?: number | null;
  referenceNumber?: string | null;
  notes?: string | null;
}

export interface StatementResponse {
  customerId: number;
  customerName: string;
  from: string;
  to: string;
  openingBalancePaise: number;
  openingBalanceDisplay: string;
  entries: LedgerEntryResponse[];
  closingBalancePaise: number;
  closingBalanceDisplay: string;
}

export interface AgeingBucket {
  label: string;
  fromDays: number;
  toDays?: number | null;
  paise: number;
  display: string;
  invoiceCount: number;
}

export interface AgeingInvoice {
  invoiceId: number;
  invoiceNumber: string;
  invoiceDate: string;
  ageDays: number;
  totalPaise: number;
  outstandingPaise: number;
  outstandingDisplay: string;
}

export interface AgeingResponse {
  customerId: number;
  customerName: string;
  asOf: string;
  buckets: AgeingBucket[];
  invoices: AgeingInvoice[];
  totalOutstandingPaise: number;
  totalOutstandingDisplay: string;
}

export interface LedgerAdjustmentRequest {
  amountPaise: number;
  /** true = the customer owes more; false = the customer owes less. */
  debit: boolean;
  reason: string;
}
