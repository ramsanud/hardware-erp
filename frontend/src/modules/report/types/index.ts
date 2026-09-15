/** Backend: report/dto/ReportDtos.java (CR-086). Every money field is paise plus a server-formatted display string. */

export interface ReportPeriod {
  from: string;
  to: string;
}

export type DayBookKind = 'SALE' | 'RECEIPT' | 'CREDIT_NOTE' | 'PURCHASE' | 'EXPENSE';

export interface DayBookEntry {
  date: string;
  kind: DayBookKind;
  reference: string;
  party: string;
  detail?: string | null;
  amountPaise: number;
  amountDisplay: string;
}

export interface DayBookTotals {
  salesPaise: number; salesDisplay: string;
  receiptsPaise: number; receiptsDisplay: string;
  creditNotesPaise: number; creditNotesDisplay: string;
  purchasesPaise: number; purchasesDisplay: string;
  expensesPaise: number; expensesDisplay: string;
  netCashPaise: number; netCashDisplay: string;
}

export interface DayBookReport {
  period: ReportPeriod;
  entries: DayBookEntry[];
  totals: DayBookTotals;
}

export interface AgeingRow {
  customerId: number;
  customerName: string;
  mobileNo: string;
  current0To30Paise: number; current0To30Display: string;
  days31To60Paise: number; days31To60Display: string;
  days61To90Paise: number; days61To90Display: string;
  over90Paise: number; over90Display: string;
  totalPaise: number; totalDisplay: string;
  openInvoices: number;
}

export interface AgeingTotals {
  current0To30Paise: number; current0To30Display: string;
  days31To60Paise: number; days31To60Display: string;
  days61To90Paise: number; days61To90Display: string;
  over90Paise: number; over90Display: string;
  totalPaise: number; totalDisplay: string;
}

export interface ReceivablesAgeingReport {
  asOf: string;
  rows: AgeingRow[];
  totals: AgeingTotals;
}

export interface StockValuationRow {
  productId: number;
  productCode: string;
  productName: string;
  categoryName?: string | null;
  unit: string;
  quantityOnHand: number;
  purchasePricePaise: number; purchasePriceDisplay: string;
  sellingPricePaise: number; sellingPriceDisplay: string;
  costValuePaise: number; costValueDisplay: string;
  sellingValuePaise: number; sellingValueDisplay: string;
}

export interface StockValuationReport {
  asOf: string;
  rows: StockValuationRow[];
  totals: {
    products: number;
    costValuePaise: number; costValueDisplay: string;
    sellingValuePaise: number; sellingValueDisplay: string;
  };
}

export interface PurchaseRegisterRow {
  purchaseId: number;
  purchaseDate: string;
  purchaseNumber: string;
  supplierBillNumber?: string | null;
  supplierName: string;
  supplierGstNo?: string | null;
  status: string;
  taxablePaise: number; taxableDisplay: string;
  cgstPaise: number; cgstDisplay: string;
  sgstPaise: number; sgstDisplay: string;
  igstPaise: number; igstDisplay: string;
  totalPaise: number; totalDisplay: string;
  paidPaise: number; paidDisplay: string;
  balancePaise: number; balanceDisplay: string;
}

export interface PurchaseRegisterReport {
  period: ReportPeriod;
  rows: PurchaseRegisterRow[];
  totals: {
    bills: number;
    taxablePaise: number; taxableDisplay: string;
    cgstPaise: number; cgstDisplay: string;
    sgstPaise: number; sgstDisplay: string;
    igstPaise: number; igstDisplay: string;
    totalPaise: number; totalDisplay: string;
    paidPaise: number; paidDisplay: string;
    balancePaise: number; balanceDisplay: string;
  };
}

export interface GstRateRow {
  ratePercent: number | null;
  taxablePaise: number; taxableDisplay: string;
  cgstPaise: number; cgstDisplay: string;
  sgstPaise: number; sgstDisplay: string;
  igstPaise: number; igstDisplay: string;
  totalTaxPaise: number; totalTaxDisplay: string;
}

export interface GstSection {
  title: string;
  rows: GstRateRow[];
  totals: GstRateRow;
}

export interface GstSummaryReport {
  period: ReportPeriod;
  outward: GstSection;
  creditNotes: GstSection;
  inward: GstSection;
  netCgstPaise: number; netCgstDisplay: string;
  netSgstPaise: number; netSgstDisplay: string;
  netIgstPaise: number; netIgstDisplay: string;
  netTaxPaise: number; netTaxDisplay: string;
}

/** The offline-tool document (CR-087). Only the parts the preview counts are typed. */
export interface Gstr1Document {
  gstin: string;
  fp: string;
  b2b: { ctin: string; inv: unknown[] }[];
  b2cl: { pos: string; inv: unknown[] }[];
  b2cs: unknown[];
  cdnr: { ctin: string; nt: unknown[] }[];
  cdnur: unknown[];
  hsn: { data: unknown[] };
}

export type ReportFormat = 'pdf' | 'xlsx';
