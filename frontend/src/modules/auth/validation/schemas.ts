import { z } from 'zod';

/**
 * Mirrors the backend Bean Validation constraints exactly.
 *
 * This exists for immediate feedback only. The backend re-validates every field
 * and is the authority: a client with these rules removed still cannot create
 * an invalid user.
 */

// CreateUserRequest.password: 8-72 chars, at least one letter and one digit
const passwordRules = z
  .string()
  .min(8, 'Password must be between 8 and 72 characters')
  .max(72, 'Password must be between 8 and 72 characters')
  .regex(/[A-Za-z]/, 'Password must contain at least one letter')
  .regex(/\d/, 'Password must contain at least one number');

// ^[6-9]\d{9}$
const mobileRules = z
  .string()
  .trim()
  .regex(/^[6-9]\d{9}$/, 'Enter a valid 10-digit Indian mobile number');

const optionalEmail = z
  .string()
  .trim()
  .max(255, 'Email is too long')
  .email('Enter a valid email address')
  .optional()
  .or(z.literal(''));

export const loginSchema = z.object({
  identifier: z.string().trim().min(1, 'Mobile number or email is required').max(255),
  password: z.string().min(1, 'Password is required').max(128),
});

export const forgotPasswordSchema = z.object({
  identifier: z.string().trim().min(1, 'Mobile number or email is required').max(255),
});

export const resetPasswordSchema = z
  .object({
    newPassword: passwordRules,
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Current password is required'),
    newPassword: passwordRules,
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  })
  // Mirrors AuthServiceImpl.changePassword, which rejects an unchanged password.
  .refine((data) => data.currentPassword !== data.newPassword, {
    message: 'New password must be different from the current one',
    path: ['newPassword'],
  });

export const profileSchema = z.object({
  fullName: z.string().trim().min(1, 'Full name is required').max(200),
  email: optionalEmail,
});

export const createUserSchema = z.object({
  fullName: z.string().trim().min(1, 'Full name is required').max(200),
  mobileNo: mobileRules,
  email: optionalEmail,
  employeeCode: z.string().trim().max(30, 'Employee code must be 30 characters or fewer').optional().or(z.literal('')),
  roleId: z.coerce.number().int().positive('Role is required'),
  password: passwordRules,
  mustChangePassword: z.boolean(),
});

export const updateUserSchema = z.object({
  fullName: z.string().trim().min(1, 'Full name is required').max(200),
  mobileNo: mobileRules,
  email: optionalEmail,
  employeeCode: z.string().trim().max(30).optional().or(z.literal('')),
  roleId: z.coerce.number().int().positive('Role is required'),
  status: z.enum(['ACTIVE', 'INACTIVE', 'SUSPENDED']),
});

export const resetUserPasswordSchema = z.object({
  newPassword: passwordRules,
});

// ^[A-Z][A-Z0-9_]{1,29}$
export const roleSchema = z.object({
  code: z
    .string()
    .trim()
    .regex(/^[A-Z][A-Z0-9_]{1,29}$/, 'Use uppercase letters, digits and underscores'),
  name: z.string().trim().min(1, 'Role name is required').max(100),
  description: z.string().trim().max(255).optional().or(z.literal('')),
  permissions: z.array(z.string()).min(1, 'Select at least one permission'),
  status: z.enum(['ACTIVE', 'INACTIVE']),
});

export type LoginValues = z.infer<typeof loginSchema>;
export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>;
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;
export type ProfileValues = z.infer<typeof profileSchema>;
export type CreateUserValues = z.infer<typeof createUserSchema>;
export type UpdateUserValues = z.infer<typeof updateUserSchema>;
export type ResetUserPasswordValues = z.infer<typeof resetUserPasswordSchema>;
export type RoleValues = z.infer<typeof roleSchema>;
