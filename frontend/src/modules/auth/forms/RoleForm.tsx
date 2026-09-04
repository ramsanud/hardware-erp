import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { DialogFooter } from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { ROLE_STATUS_OPTIONS } from '../constants';
import { roleSchema, type RoleValues } from '../validation/schemas';
import type { PermissionGroupResponse, RoleResponse } from '../types';
import { PermissionPicker } from '../components/PermissionPicker';

/** CR-053 backlog item 6 - common role labels a hardware shop actually uses, offered as suggestions on the free-text name field. */
const ROLE_NAME_SUGGESTIONS = [
  'Partner', 'Salesman', 'Stock Manager', 'Delivery Boy', 'CA', 'Cashier', 'Godown Staff',
];

interface RoleFormProps {
  role?: RoleResponse;
  permissionGroups: PermissionGroupResponse[];
  onSubmit: (values: RoleValues) => Promise<void>;
  onCancel: () => void;
}

export function RoleForm({ role, permissionGroups, onSubmit, onCancel }: RoleFormProps) {
  const isEdit = Boolean(role);
  const isSystemRole = role?.systemRole ?? false;
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register, control, handleSubmit, setError, formState: { errors, isSubmitting },
  } = useForm<RoleValues>({
    resolver: zodResolver(roleSchema),
    defaultValues: {
      code: role?.code ?? '',
      name: role?.name ?? '',
      description: role?.description ?? '',
      permissions: role?.permissions ?? [],
      status: role?.status ?? 'ACTIVE',
    },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError) {
        Object.entries(error.fieldErrors ?? {}).forEach(([field, message]) => {
          setError(field as keyof RoleValues, { message });
        });
        if (error.code === 'DUPLICATE_RESOURCE') {
          setError(error.message.toLowerCase().includes('code') ? 'code' : 'name',
            { message: error.message });
        }
        setFormError(error.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {formError ? (
        <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert>
      ) : null}

      {isSystemRole ? (
        <Alert variant="warning">
          <AlertDescription className="text-sm">
            This is a system role. Its code cannot be changed and it cannot be deleted.
            {role?.code === 'OWNER'
              ? ' The owner role must also keep every permission, or the shop could lock itself out of its own administration.'
              : ''}
          </AlertDescription>
        </Alert>
      ) : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="code" label="Role code" error={errors.code?.message} required
                   hint="Uppercase letters, digits and underscores.">
          <Input id="code" placeholder="STOCK_CLERK" readOnly={isSystemRole} disabled={isSystemRole}
                 aria-invalid={Boolean(errors.code)} {...register('code')} />
        </FormField>

        <FormField id="name" label="Display name" error={errors.name?.message} required
                   hint={isSystemRole
                     ? 'Renaming this is purely cosmetic - the underlying code and permissions are unaffected.'
                     : undefined}>
          <Input id="name" list="role-name-suggestions" placeholder="Stock Clerk"
                 aria-invalid={Boolean(errors.name)} {...register('name')} />
          {/* CR-053 backlog item 6. Suggestions only, not a fixed enum - free
              text still works for a shop whose roles don't match any of these. */}
          <datalist id="role-name-suggestions">
            {ROLE_NAME_SUGGESTIONS.map((option) => <option key={option} value={option} />)}
          </datalist>
        </FormField>

        <FormField id="description" label="Description" error={errors.description?.message}
                   className="sm:col-span-2">
          <Input id="description" placeholder="Godown staff: receives stock, cannot bill"
                 {...register('description')} />
        </FormField>

        <FormField id="status" label="Status" error={errors.status?.message} required>
          <Controller
            control={control}
            name="status"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}
                      disabled={role?.code === 'OWNER'}>
                <SelectTrigger id="status"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {ROLE_STATUS_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </FormField>
      </div>

      <FormField id="permissions" label="Permissions" error={errors.permissions?.message} required>
        <Controller
          control={control}
          name="permissions"
          render={({ field }) => (
            <div className="max-h-[45vh] overflow-y-auto rounded-md border p-3">
              <PermissionPicker
                groups={permissionGroups}
                selected={field.value}
                onChange={field.onChange}
                disabled={role?.code === 'OWNER'}
              />
            </div>
          )}
        />
      </FormField>

      {isEdit ? (
        <Alert>
          <AlertDescription className="text-sm">
            Changing permissions signs out every user holding this role, so the change
            takes effect immediately rather than when their tokens expire.
          </AlertDescription>
        </Alert>
      ) : null}

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting}>
          {isEdit ? 'Save changes' : 'Create role'}
        </Button>
      </DialogFooter>
    </form>
  );
}
