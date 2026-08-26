import { useState } from 'react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { FormField } from '@/shared/components/FormField';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { customerService } from '@/modules/customer/services/customerService';
import type { CustomerCreditCheckResponse } from '@/modules/customer/types';

const rupees = (paise: number) =>
  (paise / 100).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

interface CreditLimitWarningProps {
  check: CustomerCreditCheckResponse;
  projectedPaise: number;
  /** Re-runs the credit check so the warning clears once the limit is raised. */
  onLimitChanged: () => void;
}

/**
 * The over-limit warning, with a way out of it.
 *
 * It used to be a dead end: it told the user the sale would breach the
 * customer's credit limit and offered nothing to do about it. Raising the
 * limit meant abandoning a part-built invoice, navigating to Customers,
 * editing, and starting the wizard again - so in practice the warning was
 * ignored, which is the worst outcome for a control that exists to be
 * noticed.
 */
export function CreditLimitWarning({ check, projectedPaise, onLimitChanged }: CreditLimitWarningProps) {
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [newLimit, setNewLimit] = useState(String(Math.ceil(projectedPaise / 100)));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const overBy = projectedPaise - check.creditLimitPaise;

  const save = async () => {
    const rupeeValue = Number(newLimit);
    if (!Number.isFinite(rupeeValue) || rupeeValue < 0) {
      setError('Enter a valid amount.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      // No partial-update endpoint exists for customers, so read the current
      // record and write it back with only the limit changed. Fetched here
      // rather than reused from the wizard so a concurrent edit elsewhere is
      // not silently overwritten with stale field values.
      const current = await customerService.get(check.customerId);
      await customerService.update(check.customerId, {
        customerName: current.customerName,
        mobileNo: current.mobileNo,
        email: current.email ?? null,
        gstNo: current.gstNo ?? null,
        addressLine1: current.addressLine1 ?? null,
        addressLine2: current.addressLine2 ?? null,
        city: current.city ?? null,
        stateCode: current.stateCode ?? null,
        pincode: current.pincode ?? null,
        creditLimitPaise: Math.round(rupeeValue * 100),
        status: current.status,
      });
      toast.success(`Credit limit for ${current.customerName} set to ₹${rupees(Math.round(rupeeValue * 100))}.`);
      setOpen(false);
      onLimitChanged();
    } catch (caught) {
      toast.error(caught, 'Could not update the credit limit.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Alert variant="warning">
        <AlertDescription className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <span>
            {check.customerName}'s outstanding balance would become{' '}
            <strong className="tabular">₹{rupees(projectedPaise)}</strong>, over their{' '}
            <strong className="tabular">₹{rupees(check.creditLimitPaise)}</strong> limit
            {' '}by <strong className="tabular">₹{rupees(overBy)}</strong>.
          </span>
          <PermissionGate permission={PERMISSIONS.CUSTOMER_MANAGE}>
            <Button type="button" variant="outline" size="sm" className="shrink-0"
                    onClick={() => { setNewLimit(String(Math.ceil(projectedPaise / 100))); setOpen(true); }}>
              Raise limit
            </Button>
          </PermissionGate>
        </AlertDescription>
      </Alert>

      <Dialog open={open} onOpenChange={(next) => { if (!next) setOpen(false); }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Credit limit for {check.customerName}</DialogTitle>
          </DialogHeader>

          <div className="space-y-3 text-sm">
            <div className="flex justify-between border-b pb-2">
              <span className="text-muted-foreground">Current limit</span>
              <span className="tabular">₹{rupees(check.creditLimitPaise)}</span>
            </div>
            <div className="flex justify-between border-b pb-2">
              <span className="text-muted-foreground">Balance after this sale</span>
              <span className="tabular">₹{rupees(projectedPaise)}</span>
            </div>

            <FormField id="newCreditLimit" label="New limit (₹)" error={error ?? undefined}
                       hint="Pre-filled with just enough to cover this sale. Set 0 for no limit.">
              <Input id="newCreditLimit" type="number" min={0} step="1" autoFocus
                     value={newLimit} onChange={(e) => setNewLimit(e.target.value)} />
            </FormField>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="button" onClick={save} loading={saving}>Save limit</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
