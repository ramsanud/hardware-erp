import { useEffect, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Loader2, Plus, X } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { NumberInput } from '@/shared/components/ui/number-input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { workTypeService } from '../services/workTypeService';
import { CustomerPicker } from '../components/CustomerPicker';
import { WorkTypeQuickAddDialog } from './WorkTypeQuickAddDialog';
import { projectSchema, type ProjectValues } from '../validation/schemas';
import type { CustomerSummaryResponse } from '@/modules/customer/types';
import type { ProjectRequest, ProjectResponse, WorkTypeResponse } from '../types';

interface ProjectFormProps {
  project?: ProjectResponse;
  onSubmit: (request: ProjectRequest) => Promise<void>;
  onCancel: () => void;
}

export function ProjectForm({ project, onSubmit, onCancel }: ProjectFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const [workTypes, setWorkTypes] = useState<WorkTypeResponse[]>([]);
  const [addingWorkType, setAddingWorkType] = useState(false);
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerSummaryResponse | null>(
    project ? { id: project.customerId, customerName: project.customerName, customerCode: '', mobileNo: '', status: 'ACTIVE' } : null,
  );

  const {
    register, control, handleSubmit, setValue, formState: { errors, isSubmitting },
  } = useForm<ProjectValues>({
    resolver: zodResolver(projectSchema),
    defaultValues: {
      projectName: project?.projectName ?? '',
      customerId: project?.customerId ?? 0,
      workTypeId: project?.workTypeId ?? 0,
      description: project?.description ?? '',
      siteAddress: project?.siteAddress ?? '',
      startDate: project?.startDate ?? '',
      expectedCompletionDate: project?.expectedCompletionDate ?? '',
      customerDeadline: project?.customerDeadline ?? '',
      projectValueRupees: project ? Number(project.projectValueDisplay.replace(/,/g, '')) : 0,
      notes: project?.notes ?? '',
    },
  });

  useEffect(() => {
    workTypeService.list().then(setWorkTypes).catch(() => setWorkTypes([]));
  }, []);

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit({
        projectName: values.projectName,
        customerId: values.customerId,
        workTypeId: values.workTypeId,
        description: values.description || null,
        siteAddress: values.siteAddress || null,
        startDate: values.startDate || null,
        expectedCompletionDate: values.expectedCompletionDate || null,
        customerDeadline: values.customerDeadline || null,
        projectValuePaise: Math.round(values.projectValueRupees * 100),
        notes: values.notes || null,
      });
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : 'Something went wrong. Please try again.');
    }
  });

  return (
    <form onSubmit={submit} className="max-w-3xl space-y-6" noValidate>
      {formError ? <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert> : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="projectName" label="Project name" error={errors.projectName?.message} required className="sm:col-span-2">
          <Input id="projectName" autoFocus placeholder="e.g. Ram Sangar's Modular Kitchen" {...register('projectName')} />
        </FormField>

        <FormField id="customer" label="Customer" error={errors.customerId?.message} required>
          {selectedCustomer ? (
            <div className="flex items-center justify-between rounded-md border px-3 py-2 text-sm">
              <span>
                <span className="font-medium">{selectedCustomer.customerName}</span>
                {selectedCustomer.mobileNo ? <span className="text-muted-foreground"> · {selectedCustomer.mobileNo}</span> : null}
              </span>
              <button type="button" onClick={() => { setSelectedCustomer(null); setValue('customerId', 0); }}
                      className="text-muted-foreground hover:text-foreground" aria-label="Change customer">
                <X className="h-4 w-4" />
              </button>
            </div>
          ) : (
            <CustomerPicker onPick={(customer) => { setSelectedCustomer(customer); setValue('customerId', customer.id, { shouldValidate: true }); }} />
          )}
        </FormField>

        <FormField id="workType" label="Work type" error={errors.workTypeId?.message} required>
          <div className="flex gap-2">
            <Controller
              control={control}
              name="workTypeId"
              render={({ field }) => (
                <Select value={field.value ? String(field.value) : ''} onValueChange={(v) => field.onChange(Number(v))}>
                  <SelectTrigger id="workType"><SelectValue placeholder="Select a work type" /></SelectTrigger>
                  <SelectContent>
                    {workTypes.map((wt) => (
                      <SelectItem key={wt.id} value={String(wt.id)}>{wt.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            <Button type="button" variant="outline" size="icon" onClick={() => setAddingWorkType(true)} aria-label="Add work type">
              <Plus className="h-4 w-4" />
            </Button>
          </div>
        </FormField>

        <FormField id="siteAddress" label="Site address (optional)" error={errors.siteAddress?.message} className="sm:col-span-2">
          <Input id="siteAddress" {...register('siteAddress')} />
        </FormField>

        <FormField id="description" label="Description (optional)" error={errors.description?.message} className="sm:col-span-2">
          <Input id="description" {...register('description')} />
        </FormField>

        <FormField id="startDate" label="Start date (optional)" error={errors.startDate?.message}>
          <Input id="startDate" type="date" {...register('startDate')} />
        </FormField>

        <FormField id="expectedCompletionDate" label="Expected completion (optional)" error={errors.expectedCompletionDate?.message}>
          <Input id="expectedCompletionDate" type="date" {...register('expectedCompletionDate')} />
        </FormField>

        <FormField id="customerDeadline" label="Customer deadline (optional)" error={errors.customerDeadline?.message}
                   hint="Drives the overdue warning on the project list.">
          <Input id="customerDeadline" type="date" {...register('customerDeadline')} />
        </FormField>

        <FormField id="projectValueRupees" label="Project value (₹)" error={errors.projectValueRupees?.message} required
                   hint="The agreed contract value - profit is calculated against this.">
          <Controller
            control={control}
            name="projectValueRupees"
            render={({ field }) => (
              <NumberInput id="projectValueRupees" min={0} value={field.value} onChange={field.onChange} onBlur={field.onBlur} />
            )}
          />
        </FormField>

        <FormField id="notes" label="Notes (optional)" error={errors.notes?.message} className="sm:col-span-2">
          <Input id="notes" {...register('notes')} />
        </FormField>
      </div>

      <div className="flex items-center justify-end gap-3 border-t pt-4">
        <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>Cancel</Button>
        <Button type="submit" loading={isSubmitting}>
          {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
          {project ? 'Save changes' : 'Create project'}
        </Button>
      </div>

      <WorkTypeQuickAddDialog
        open={addingWorkType}
        onOpenChange={setAddingWorkType}
        onCreated={(created) => {
          setWorkTypes((current) => [...current, created]);
          setValue('workTypeId', created.id, { shouldValidate: true });
        }}
      />
    </form>
  );
}
