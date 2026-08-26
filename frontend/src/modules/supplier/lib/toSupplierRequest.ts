import type { SupplierRequest } from '../types';
import type { SupplierValues } from '../validation/schemas';

/** Empty optional fields must become null, or the backend keeps stale values. */
function blankToNull(value?: string): string | null {
  return value ? value : null;
}

/**
 * Converts the form's schema shape into the wire DTO.
 *
 * The only real transformation is money: the form collects rupees for a
 * human to read; the backend stores paise as a BIGINT (CLAUDE.md money rule).
 * Multiplying by 100 and rounding keeps this a single, deliberate boundary
 * crossing rather than floating-point rupees leaking into the request.
 */
export function toSupplierRequest(values: SupplierValues): SupplierRequest {
  return {
    supplierCode: values.supplierCode || undefined,
    supplierName: values.supplierName,
    contactPerson: blankToNull(values.contactPerson),
    mobileNo: values.mobileNo,
    alternateMobileNo: blankToNull(values.alternateMobileNo),
    email: blankToNull(values.email),
    gstNo: blankToNull(values.gstNo),
    panNo: blankToNull(values.panNo),
    addressLine1: blankToNull(values.addressLine1),
    addressLine2: blankToNull(values.addressLine2),
    city: blankToNull(values.city),
    stateCode: blankToNull(values.stateCode),
    pincode: blankToNull(values.pincode),
    paymentTermsDays: values.paymentTermsDays,
    creditLimitPaise: Math.round(values.creditLimitRupees * 100),
    bankAccountName: blankToNull(values.bankAccountName),
    bankAccountNo: blankToNull(values.bankAccountNo),
    bankIfsc: blankToNull(values.bankIfsc),
    bankName: blankToNull(values.bankName),
    status: values.status,
    remarks: blankToNull(values.remarks),
  };
}
