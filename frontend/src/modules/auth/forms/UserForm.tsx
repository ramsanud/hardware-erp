import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Checkbox } from '@/shared/components/ui/checkbox';
import { Label } from '@/shared/components/ui/label';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  DialogFooter,
} from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { USER_STATUS_OPTIONS } from '../constants';
import {
  createUserSchema, updateUserSchema,
  type CreateUserValues, type UpdateUserValues,
} from '../validation/schemas';
import type { RoleResponse, UserResponse } from '../types';
import { PASSWORD_HINT, PasswordInput } from './PasswordFields';

/**
 * Create and edit submit different shapes: CreateUserRequest carries password
 * and mustChangePassword, UpdateUserRequest carries status. The union is
 * exported so the page handlers are typed rather than falling back to `any`.
 */
export type UserFormValues = CreateUserValues & Partial<UpdateUserValues>;

interface UserFormProps {
  /** Undefined means create. */
  user?: UserResponse;
  roles: RoleResponse[];
  onSubmit: (values: UserFormValues) => Promise<void>;
  onCancel: () => void;
}

/**
 * One component for create and edit.
 *
 * The two backend DTOs differ: CreateUserRequest carries a password and
 * mustChangePassword; UpdateUserRequest carries a status instead. The schema is
 * chosen accordingly rather than sending fields the endpoint would ignore.
 */
export function UserForm({ user, roles, onSubmit, onCancel }: UserFormProps) {
  const isEdit = Boolean(user);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register, control, handleSubmit, setError, formState: { errors, isSubmitting },
  } = useForm<UserFormValues>({
    resolver: zodResolver(isEdit ? updateUserSchema : createUserSchema) as never,
    defaultValues: {
      fullName: user?.fullName ?? '',
      mobileNo: user?.mobileNo ?? '',
      email: user?.email ?? '',
      employeeCode: user?.employeeCode ?? '',
      roleId: user?.roleId ?? 0,
      password: '',
      mustChangePassword: true,
      status: user?.status ?? 'ACTIVE',
    },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof UserFormValues, { message });
        });
        // 409 duplicates name the field in the message, not in fieldErrors.
        if (error.code === 'DUPLICATE_RESOURCE') {
          const lower = error.message.toLowerCase();
          if (lower.includes('mobile')) setError('mobileNo', { message: error.message });
          else if (lower.includes('email')) setError('email', { message: error.message });
          else if (lower.includes('employee')) setError('employeeCode', { message: error.message });
        }
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  const assignableRoles = roles.filter((role) => role.status === 'ACTIVE');

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {formError ? (
        <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="fullName" label="Full name" error={errors.fullName?.message} required
                   className="sm:col-span-2">
          <Input id="fullName" autoFocus aria-invalid={Boolean(errors.fullName)}
                 {...register('fullName')} />
        </FormField>

        <FormField id="mobileNo" label="Mobile number" error={errors.mobileNo?.message} required>
          <Input id="mobileNo" inputMode="numeric" maxLength={10} placeholder="9876543210"
                 aria-invalid={Boolean(errors.mobileNo)} {...register('mobileNo')} />
        </FormField>

        <FormField id="employeeCode" label="Employee code" error={errors.employeeCode?.message}>
          <Input id="employeeCode" placeholder="EMP005"
                 aria-invalid={Boolean(errors.employeeCode)} {...register('employeeCode')} />
        </FormField>

        <FormField id="email" label="Email" error={errors.email?.message}
                   hint="Required to receive password reset links."
                   className="sm:col-span-2">
          <Input id="email" type="email" aria-invalid={Boolean(errors.email)}
                 {...register('email')} />
        </FormField>

        <FormField id="roleId" label="Role" error={errors.roleId?.message} required>
          <Controller
            control={control}
            name="roleId"
            render={({ field }) => (
              <Select
                value={field.value ? String(field.value) : undefined}
                onValueChange={(value) => field.onChange(Number(value))}
              >
                <SelectTrigger id="roleId" aria-invalid={Boolean(errors.roleId)}>
                  <SelectValue placeholder="Select a role" />
                </SelectTrigger>
                <SelectContent>
                  {assignableRoles.map((role) => (
                    <SelectItem key={role.id} value={String(role.id)}>{role.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </FormField>

        {isEdit ? (
          <FormField id="status" label="Status" error={errors.status?.message} required>
            <Controller
              control={control}
              name="status"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="status"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {USER_STATUS_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
        ) : (
          <PasswordInput
            id="password" label="Temporary password" hint={PASSWORD_HINT}
            error={errors.password}
            registration={register('password')}
          />
        )}
      </div>

      {isEdit ? (
        <Alert>
          <AlertDescription className="text-sm">
            Changing the role or status signs this user out on every device immediately.
          </AlertDescription>
        </Alert>
      ) : (
        <Controller
          control={control}
          name="mustChangePassword"
          render={({ field }) => (
            <label htmlFor="mustChangePassword" className="flex cursor-pointer items-start gap-2.5">
              <Checkbox
                id="mustChangePassword"
                checked={field.value}
                onCheckedChange={(checked) => field.onChange(checked === true)}
                className="mt-0.5"
              />
              <span>
                <Label htmlFor="mustChangePassword" className="cursor-pointer">
                  Require a password change at first sign-in
                </Label>
                <span className="mt-0.5 block text-sm text-muted-foreground">
                  Recommended: you know this temporary password, so the employee should replace it.
                </span>
              </span>
            </label>
          )}
        />
      )}

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {isEdit ? 'Save changes' : 'Create user'}
        </Button>
      </DialogFooter>
    </form>
  );
}
