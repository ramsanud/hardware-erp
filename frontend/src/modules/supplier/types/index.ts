/**
 * Mirrors backend supplier/dto/*.java exactly.
 * Changing a name here without changing the DTO breaks at runtime, not compile
 * time, so these must be kept in step with the backend records.
 */

export type SupplierStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED';

// ---- requests ----

export interface SupplierRequest {
  /** Leave blank on create; the system generates SUP-0001, SUP-0002 ... */
  supplierCode?: string;
  supplierName: string;
  contactPerson?: string | null;
  mobileNo: string;
  alternateMobileNo?: string | null;
  email?: string | null;
  gstNo?: string | null;
  panNo?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  /** GST state code, e.g. "33" for Tamil Nadu. */
  stateCode?: string | null;
  pincode?: string | null;
  /** Credit period in days. 0 means cash on delivery. */
  paymentTermsDays: number;
  /** Credit ceiling in paise. */
  creditLimitPaise: number;
  bankAccountName?: string | null;
  bankAccountNo?: string | null;
  bankIfsc?: string | null;
  bankName?: string | null;
  status: SupplierStatus;
  remarks?: string | null;
}

export interface SupplierContactRequest {
  contactName: string;
  designation?: string | null;
  mobileNo: string;
  email?: string | null;
  /** Only one contact per supplier may be primary. */
  primary: boolean;
}

// ---- responses ----

export interface SupplierContactResponse {
  id: number;
  contactName: string;
  designation?: string | null;
  mobileNo: string;
  email?: string | null;
  primary: boolean;
}

export interface SupplierResponse {
  id: number;
  supplierCode: string;
  supplierName: string;
  contactPerson?: string | null;
  mobileNo: string;
  alternateMobileNo?: string | null;
  email?: string | null;
  gstNo?: string | null;
  panNo?: string | null;

  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  stateCode?: string | null;
  pincode?: string | null;

  paymentTermsDays: number;
  /** Credit ceiling in paise. */
  creditLimitPaise: number;
  /** Same value formatted for display, e.g. "5,00,000.00". */
  creditLimitDisplay: string;

  bankAccountName?: string | null;
  /** Masked. Only the last four digits are returned. */
  bankAccountNo?: string | null;
  bankIfsc?: string | null;
  bankName?: string | null;

  status: SupplierStatus;
  remarks?: string | null;
  contacts: SupplierContactResponse[];

  createdAt?: string | null;
  updatedAt?: string | null;
}

/**
 * The list-screen projection. Deliberately omits bank details and remarks -
 * mirrors SupplierSummaryResponse.
 */
export interface SupplierSummaryResponse {
  id: number;
  supplierCode: string;
  supplierName: string;
  contactPerson?: string | null;
  mobileNo: string;
  city?: string | null;
  gstNo?: string | null;
  paymentTermsDays: number;
  creditLimitDisplay: string;
  status: SupplierStatus;
}

/**
 * CR-058 - the recycle-bin projection. Deliberately narrower than
 * SupplierSummaryResponse: a deleted row is only being identified and
 * restored, so no credit limit, GST number or contact details are sent.
 */
export interface SupplierDeletedResponse {
  id: number;
  supplierCode: string;
  supplierName: string;
  mobileNo: string;
  city?: string | null;
  deletedAt: string;
}

// ---- query params ----

export interface SupplierSearchParams {
  search?: string;
  status?: SupplierStatus;
  city?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}
