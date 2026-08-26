import { z } from 'zod';

export const workerSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(150),
  mobileNo: z.string().trim().max(15).optional().or(z.literal('')),
  roleTitle: z.string().trim().max(100).optional().or(z.literal('')),
  dailyRateRupees: z.string().trim().min(1, 'Enter a daily rate').refine((v) => Number(v) > 0, 'Must be greater than zero'),
});
export type WorkerValues = z.infer<typeof workerSchema>;

export const workerPaymentSchema = z.object({
  amountRupees: z.string().trim().min(1, 'Enter an amount').refine((v) => Number(v) > 0, 'Must be greater than zero'),
  paymentDate: z.string().trim().min(1, 'Date is required'),
  paymentMethod: z.enum(['CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER']),
  notes: z.string().trim().max(500).optional().or(z.literal('')),
});
export type WorkerPaymentValues = z.infer<typeof workerPaymentSchema>;
