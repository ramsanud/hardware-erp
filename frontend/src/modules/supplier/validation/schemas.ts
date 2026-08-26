import { z } from 'zod';

/**
 * Mirrors the backend Bean Validation constraints on SupplierRequest and
 * SupplierContactRequest exactly.
 *
 * This exists for immediate feedback only. The backend re-validates every
 * field and is the authority: a client with these rules removed still cannot
 * create an invalid supplier.
 */

// ^[6-9]\d{9}$
const mobileRules = z
  .string()
  .trim()
  .regex(/^[6-9]\d{9}$/, 'Enter a valid 10-digit Indian mobile number');

// ^$|^[6-9]\d{9}$
const optionalMobile = z
  .string()
  .trim()
  .regex(/^$|^[6-9]\d{9}$/, 'Enter a valid 10-digit Indian mobile number')
  .optional()
  .or(z.literal(''));

const optionalEmail = z
  .string()
  .trim()
  .max(255, 'Email is too long')
  .email('Enter a valid email address')
  .optional()
  .or(z.literal(''));

export const supplierSchema = z.object({
  // ^$|^[A-Z0-9][A-Z0-9-]{1,29}$
  supplierCode: z
    .string()
    .trim()
    .max(30, 'Supplier code must be 30 characters or fewer')
    .regex(/^$|^[A-Z0-9][A-Z0-9-]{1,29}$/, 'Use uppercase letters, digits and hyphens')
    .optional()
    .or(z.literal('')),
  supplierName: z.string().trim().min(1, 'Supplier name is required').max(255),
  contactPerson: z.string().trim().max(200).optional().or(z.literal('')),
  mobileNo: mobileRules,
  alternateMobileNo: optionalMobile,
  email: optionalEmail,
  // ^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]$
  gstNo: z
    .string()
    .trim()
    .regex(/^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]$/, 'Enter a valid 15-character GSTIN')
    .optional()
    .or(z.literal('')),
  // ^$|^[A-Z]{5}[0-9]{4}[A-Z]$
  panNo: z
    .string()
    .trim()
    .regex(/^$|^[A-Z]{5}[0-9]{4}[A-Z]$/, 'Enter a valid 10-character PAN')
    .optional()
    .or(z.literal('')),
  addressLine1: z.string().trim().max(255).optional().or(z.literal('')),
  addressLine2: z.string().trim().max(255).optional().or(z.literal('')),
  city: z.string().trim().max(100).optional().or(z.literal('')),
  // ^$|^[0-9]{2}$
  stateCode: z
    .string()
    .trim()
    .regex(/^$|^[0-9]{2}$/, 'State code must be two digits')
    .optional()
    .or(z.literal('')),
  // ^$|^[1-9][0-9]{5}$
  pincode: z
    .string()
    .trim()
    .regex(/^$|^[1-9][0-9]{5}$/, 'Enter a valid 6-digit pincode')
    .optional()
    .or(z.literal('')),
  paymentTermsDays: z.coerce
    .number()
    .int('Payment terms must be a whole number of days')
    .min(0, 'Payment terms cannot be negative')
    .max(365, 'Payment terms cannot exceed 365 days'),
  // Entered in rupees; converted to creditLimitPaise before the request is sent.
  creditLimitRupees: z.coerce.number().min(0, 'Credit limit cannot be negative'),
  bankAccountName: z.string().trim().max(200).optional().or(z.literal('')),
  // Generic 9-18 digit range (real-world span across Indian banks) rather
  // than a per-bank exact length - see CR-023: no authoritative source for
  // exact lengths exists, and guessing risks rejecting real account
  // numbers. Digits only so a leading zero is never silently dropped.
  bankAccountNo: z.string().trim()
    .regex(/^$|^[0-9]{9,18}$/, 'Account number must be 9-18 digits')
    .optional().or(z.literal('')),
  // ^$|^[A-Z]{4}0[A-Z0-9]{6}$
  bankIfsc: z
    .string()
    .trim()
    .regex(/^$|^[A-Z]{4}0[A-Z0-9]{6}$/, 'Enter a valid IFSC code')
    .optional()
    .or(z.literal('')),
  bankName: z.string().trim().max(200).optional().or(z.literal('')),
  status: z.enum(['ACTIVE', 'INACTIVE', 'BLOCKED']),
  remarks: z.string().trim().optional().or(z.literal('')),
});

export const supplierContactSchema = z.object({
  contactName: z.string().trim().min(1, 'Contact name is required').max(200),
  designation: z.string().trim().max(100).optional().or(z.literal('')),
  mobileNo: mobileRules,
  email: optionalEmail,
  primary: z.boolean(),
});

export type SupplierValues = z.infer<typeof supplierSchema>;
export type SupplierContactValues = z.infer<typeof supplierContactSchema>;
