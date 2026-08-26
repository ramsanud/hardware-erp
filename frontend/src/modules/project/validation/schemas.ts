import { z } from 'zod';

export const projectSchema = z.object({
  projectName: z.string().trim().min(1, 'Project name is required').max(200),
  customerId: z.number({ message: 'Select a customer' }).int().positive('Select a customer'),
  workTypeId: z.number({ message: 'Select a work type' }).int().positive('Select a work type'),
  description: z.string().trim().max(2000).optional().or(z.literal('')),
  siteAddress: z.string().trim().max(500).optional().or(z.literal('')),
  startDate: z.string().trim().optional().or(z.literal('')),
  expectedCompletionDate: z.string().trim().optional().or(z.literal('')),
  customerDeadline: z.string().trim().optional().or(z.literal('')),
  projectValueRupees: z.number({ message: 'Enter the project value' }).min(0, 'Cannot be negative'),
  notes: z.string().trim().max(2000).optional().or(z.literal('')),
});
export type ProjectValues = z.infer<typeof projectSchema>;

export const workTypeSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(100),
  description: z.string().trim().max(500).optional().or(z.literal('')),
});
export type WorkTypeValues = z.infer<typeof workTypeSchema>;

export const projectMaterialSchema = z.object({
  productId: z.number({ message: 'Select a product' }).int().positive('Select a product'),
  supplierId: z.number().int().positive().optional().nullable(),
  quantityRequired: z.number().optional().nullable(),
  quantityEstimated: z.number().optional().nullable(),
  quantityActual: z.number().optional().nullable(),
  quantityWastage: z.number().min(0).optional().nullable(),
  unitPriceRupees: z.number().min(0).optional().nullable(),
  notes: z.string().trim().max(500).optional().or(z.literal('')),
});
export type ProjectMaterialValues = z.infer<typeof projectMaterialSchema>;

export const projectExpenseSchema = z.object({
  category: z.enum(['LABOUR', 'EMPLOYEE', 'FOOD', 'STAY', 'PETROL', 'OTHER']),
  amountRupees: z.number({ message: 'Enter an amount' }).min(0.01, 'Amount must be greater than zero'),
  expenseDate: z.string().trim().min(1, 'Date is required'),
  paidTo: z.string().trim().max(200).optional().or(z.literal('')),
  description: z.string().trim().max(500).optional().or(z.literal('')),
});
export type ProjectExpenseValues = z.infer<typeof projectExpenseSchema>;

export const projectPaymentSchema = z.object({
  amountRupees: z.number({ message: 'Enter an amount' }).min(0.01, 'Amount must be greater than zero'),
  paymentMethod: z.enum(['CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER']),
  paymentDate: z.string().trim().min(1, 'Date is required'),
  notes: z.string().trim().max(500).optional().or(z.literal('')),
});
export type ProjectPaymentValues = z.infer<typeof projectPaymentSchema>;

export const rooftopCalculatorSchema = z.object({
  widthMeters: z.number({ message: 'Enter width' }).min(0.01),
  lengthMeters: z.number({ message: 'Enter length' }).min(0.01),
  sheetWidthMeters: z.number({ message: 'Enter sheet width' }).min(0.01),
  sheetLengthMeters: z.number({ message: 'Enter sheet length' }).min(0.01),
  overlapPercent: z.number().min(0).optional().nullable(),
  wastagePercent: z.number().min(0).optional().nullable(),
});
export type RooftopCalculatorValues = z.infer<typeof rooftopCalculatorSchema>;
