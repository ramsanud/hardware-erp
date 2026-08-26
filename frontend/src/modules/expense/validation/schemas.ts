import { z } from 'zod';

export const expenseSchema = z.object({
  expenseDate: z.string().trim().min(1, 'Date is required'),
  categoryId: z.number({ invalid_type_error: 'Choose a category' }).positive('Choose a category'),
  amountRupees: z.string().trim().min(1, 'Enter an amount').refine((v) => Number(v) > 0, 'Must be greater than zero'),
  paymentMethod: z.enum(['CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER']),
  notes: z.string().trim().max(500).optional().or(z.literal('')),
});
export type ExpenseValues = z.infer<typeof expenseSchema>;

export const expenseCategorySchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(100),
  description: z.string().trim().max(500).optional().or(z.literal('')),
});
export type ExpenseCategoryValues = z.infer<typeof expenseCategorySchema>;
