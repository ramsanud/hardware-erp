import { z } from 'zod';

export const customerSchema = z.object({
  customerName: z.string().trim().min(1, 'Customer name is required').max(255),
  mobileNo: z.string().trim().regex(/^[6-9]\d{9}$/, 'Enter a valid 10-digit mobile number'),
  email: z.string().trim().email('Enter a valid email address').optional().or(z.literal('')),
  gstNo: z.string().trim().toUpperCase()
    .regex(/^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/, 'Enter a valid 15-character GSTIN')
    .optional().or(z.literal('')),
  addressLine1: z.string().trim().max(255).optional().or(z.literal('')),
  addressLine2: z.string().trim().max(255).optional().or(z.literal('')),
  city: z.string().trim().max(100).optional().or(z.literal('')),
  stateCode: z.string().trim().regex(/^\d{2}$/, 'State code is 2 digits').optional().or(z.literal('')),
  pincode: z.string().trim().regex(/^\d{6}$/, 'Enter a valid 6-digit pincode').optional().or(z.literal('')),
  creditLimitRupees: z.coerce.number().min(0, 'Credit limit cannot be negative').optional(),
  status: z.enum(['ACTIVE', 'INACTIVE']),
  whatsappOptIn: z.boolean(),
});

export type CustomerValues = z.infer<typeof customerSchema>;
