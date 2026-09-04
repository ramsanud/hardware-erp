import { useEffect, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Flag, Plus, Trash2 } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import { Card, CardContent } from '@/shared/components/ui/card';
import { Input } from '@/shared/components/ui/input';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { usePlatformAdminAuth } from '../hooks/PlatformAdminAuthProvider';
import { platformAdminFeatureFlagService } from '../services/platformAdminFeatureFlagService';
import type { FeatureFlagResponse } from '../types';

const flagSchema = z.object({
  flagKey: z.string().trim().min(1, 'Enter a key').max(100).regex(/^[a-z0-9_.-]+$/, 'Lowercase letters, numbers, _ . or - only'),
  name: z.string().trim().min(1, 'Enter a name').max(200),
  description: z.string().trim().max(1000).optional().or(z.literal('')),
  scope: z.enum(['GLOBAL', 'TENANT', 'PLAN']),
});
type FlagFormValues = z.infer<typeof flagSchema>;

export function PlatformAdminFeatureFlagsPage() {
  const toast = useToast();
  const { admin } = usePlatformAdminAuth();
  const canManage = admin?.permissions.includes('FEATURE_FLAG_MANAGE') ?? false;

  const [flags, setFlags] = useState<FeatureFlagResponse[] | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [deleting, setDeleting] = useState<FeatureFlagResponse | null>(null);

  const load = async () => {
    setError(null);
    try {
      setFlags(await platformAdminFeatureFlagService.list());
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    }
  };

  useEffect(() => { void load(); }, []);

  const {
    register, control, handleSubmit, reset, formState: { errors, isSubmitting },
  } = useForm<FlagFormValues>({
    resolver: zodResolver(flagSchema),
    defaultValues: { flagKey: '', name: '', description: '', scope: 'GLOBAL' },
  });

  const submit = handleSubmit(async (values) => {
    try {
      await platformAdminFeatureFlagService.create({
        flagKey: values.flagKey, name: values.name, description: values.description || null, scope: values.scope,
      });
      toast.success('Feature flag created.');
      reset();
      setDialogOpen(false);
      await load();
    } catch (caught) {
      toast.error(caught, 'Could not create this flag.');
    }
  });

  const toggle = async (flag: FeatureFlagResponse) => {
    try {
      if (flag.enabled) await platformAdminFeatureFlagService.disable(flag.id);
      else await platformAdminFeatureFlagService.enable(flag.id);
      await load();
    } catch (caught) {
      toast.error(caught, 'Could not update this flag.');
    }
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await platformAdminFeatureFlagService.remove(deleting.id);
      toast.success('Feature flag deleted.');
      await load();
    } catch (caught) {
      toast.error(caught, 'Could not delete this flag.');
      throw caught;
    }
  };

  if (error) return <Card><ErrorState error={error} onRetry={load} /></Card>;
  if (!flags) return null;

  return (
    <>
      <PageHeader
        title="Feature Flags"
        description="Backend-enforced toggles - FeatureFlagService.isEnabled() is the real check, never a frontend-only flag."
        actions={canManage ? (
          <Button onClick={() => setDialogOpen(true)}>
            <Plus className="h-4 w-4" />
            <span className="hidden sm:inline">New flag</span>
          </Button>
        ) : null}
      />

      <Card>
        <CardContent className="px-0 pb-0">
          {flags.length === 0 ? (
            <EmptyState icon={Flag} title="No feature flags yet" description="Create one to gate a backend feature at runtime." />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Key</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead className="hidden md:table-cell">Scope</TableHead>
                  <TableHead>Enabled</TableHead>
                  {canManage ? <TableHead className="w-12" /> : null}
                </TableRow>
              </TableHeader>
              <TableBody>
                {flags.map((flag) => (
                  <TableRow key={flag.id}>
                    <TableCell className="font-mono text-xs">{flag.flagKey}</TableCell>
                    <TableCell>
                      <span className="font-medium">{flag.name}</span>
                      {flag.description ? <span className="mt-0.5 block text-xs text-muted-foreground">{flag.description}</span> : null}
                    </TableCell>
                    <TableCell className="hidden md:table-cell"><Badge variant="outline">{flag.scope}</Badge></TableCell>
                    <TableCell>
                      <button
                        type="button"
                        disabled={!canManage}
                        onClick={() => void toggle(flag)}
                        aria-label={flag.enabled ? `Disable ${flag.name}` : `Enable ${flag.name}`}
                      >
                        <Badge variant={flag.enabled ? 'default' : 'secondary'} className="cursor-pointer">
                          {flag.enabled ? 'On' : 'Off'}
                        </Badge>
                      </button>
                    </TableCell>
                    {canManage ? (
                      <TableCell>
                        <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => setDeleting(flag)} aria-label={`Delete ${flag.name}`}>
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </TableCell>
                    ) : null}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={(open) => { if (!isSubmitting) { setDialogOpen(open); if (!open) reset(); } }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>New feature flag</DialogTitle></DialogHeader>
          <form onSubmit={submit} className="space-y-4" noValidate>
            <FormField id="flagKey" label="Key" error={errors.flagKey?.message} required>
              <Input id="flagKey" autoFocus placeholder="e.g. new-invoice-designer" {...register('flagKey')} />
            </FormField>
            <FormField id="name" label="Name" error={errors.name?.message} required>
              <Input id="name" {...register('name')} />
            </FormField>
            <FormField id="scope" label="Scope" error={errors.scope?.message} required>
              <Controller
                control={control}
                name="scope"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="scope"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="GLOBAL">Global</SelectItem>
                      <SelectItem value="TENANT">Tenant</SelectItem>
                      <SelectItem value="PLAN">Plan</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </FormField>
            <FormField id="description" label="Description" error={errors.description?.message}>
              <Input id="description" {...register('description')} />
            </FormField>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)} disabled={isSubmitting}>Cancel</Button>
              <Button type="submit" loading={isSubmitting}>Create</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete this feature flag?"
        description={`${deleting?.name ?? 'This flag'} will be permanently removed. Any backend code checking it will get "not enabled" from then on.`}
        confirmLabel="Delete"
        destructive
        onConfirm={handleDelete}
      />
    </>
  );
}
