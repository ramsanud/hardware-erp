import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Banknote, Eye, Loader2, Pencil, Plus, QrCode, Star, Trash2, Upload, X,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Checkbox } from '@/shared/components/ui/checkbox';
import { Badge } from '@/shared/components/ui/badge';
import {
  Card, CardContent, CardHeader, CardTitle, CardDescription,
} from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/shared/components/ui/dialog';
import { Alert, AlertDescription } from '@/shared/components/ui/alert';
import { EmptyState } from '@/shared/components/EmptyState';
import { FormField } from '@/shared/components/FormField';
import { useAuthenticatedImage } from '@/shared/hooks/useAuthenticatedImage';
import { ApiError } from '@/shared/types/api';
import { useToast } from '@/modules/auth/hooks/useToast';
import { tenantBankAccountService } from '../services/tenantBankAccountService';
import type { TenantBankAccountRequest, TenantBankAccountResponse } from '../types';

const QR_ACCEPTED = ['image/png', 'image/jpeg', 'image/webp'];
const QR_MAX_BYTES = 2 * 1024 * 1024;

const accountSchema = z.object({
  label: z.string().trim().min(1, 'A label is required, e.g. "Primary account"').max(100),
  bankName: z.string().trim().min(1, 'Bank name is required').max(200),
  accountHolderName: z.string().trim().min(1, 'Account holder name is required').max(200),
  accountNumber: z.string().trim().regex(/^[0-9]{9,18}$/, 'Account number must be 9-18 digits'),
  ifscCode: z.string().trim().toUpperCase().regex(/^[A-Z]{4}0[A-Z0-9]{6}$/, 'Enter a valid IFSC code'),
  upiId: z.string().trim().regex(/^$|^[\w.\-]{2,256}@[a-zA-Z][\w]{2,64}$/, 'Enter a valid UPI ID, e.g. shopname@okicici')
    .optional().or(z.literal('')),
  defaultAccount: z.boolean(),
});
type AccountFormValues = z.infer<typeof accountSchema>;

/**
 * CR-036 - multiple accounts a shop can receive payment into, each with its
 * own set of owner-labelled QR images (GPay/PhonePe/Bank app). The invoice
 * wizard lets the owner pick which account + QR to print per invoice; this
 * card is where those accounts and QR codes are managed. The pre-existing
 * single "Bank & payment details" card above stays untouched as the
 * fallback for any invoice that never picks one of these accounts.
 */
export function BankAccountsCard() {
  const toast = useToast();
  const [accounts, setAccounts] = useState<TenantBankAccountResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [formOpen, setFormOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [editing, setEditing] = useState<TenantBankAccountResponse | null>(null);
  const [revealed, setRevealed] = useState<Record<number, string>>({});
  const [qrDialogAccount, setQrDialogAccount] = useState<TenantBankAccountResponse | null>(null);

  const {
    register, handleSubmit, reset, watch, setValue, formState: { errors, isSubmitting },
  } = useForm<AccountFormValues>({
    resolver: zodResolver(accountSchema),
    defaultValues: {
      label: '', bankName: '', accountHolderName: '', accountNumber: '', ifscCode: '', upiId: '', defaultAccount: false,
    },
  });
  const defaultAccount = watch('defaultAccount');

  const reload = () => {
    setLoading(true);
    tenantBankAccountService.list()
      .then(setAccounts)
      .catch(() => setAccounts([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => { reload(); }, []);

  const openCreate = () => {
    setEditing(null);
    reset({ label: '', bankName: '', accountHolderName: '', accountNumber: '', ifscCode: '', upiId: '', defaultAccount: accounts.length === 0 });
    setFormError(null);
    setFormOpen(true);
  };

  const openEdit = (account: TenantBankAccountResponse) => {
    setEditing(account);
    reset({
      label: account.label,
      bankName: account.bankName,
      accountHolderName: account.accountHolderName,
      accountNumber: revealed[account.id] ?? '',
      ifscCode: account.ifscCode,
      upiId: account.upiId ?? '',
      defaultAccount: account.defaultAccount,
    });
    setFormError(null);
    setFormOpen(true);
  };

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    const body: TenantBankAccountRequest = {
      label: values.label,
      bankName: values.bankName,
      accountHolderName: values.accountHolderName,
      accountNumber: values.accountNumber,
      ifscCode: values.ifscCode,
      upiId: values.upiId || null,
      defaultAccount: values.defaultAccount,
    };
    try {
      if (editing) {
        await tenantBankAccountService.update(editing.id, body);
        toast.success('Bank account updated.');
      } else {
        await tenantBankAccountService.create(body);
        toast.success('Bank account added.');
      }
      setFormOpen(false);
      reload();
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFormError(caught.message);
        return;
      }
      setFormError('Something went wrong. Please try again.');
    }
  });

  const handleDelete = async (account: TenantBankAccountResponse) => {
    try {
      await tenantBankAccountService.remove(account.id);
      toast.success(`"${account.label}" removed.`);
      reload();
    } catch (caught) {
      toast.error(caught, 'Could not remove this account.');
    }
  };

  const handleReveal = async (account: TenantBankAccountResponse) => {
    try {
      const number = await tenantBankAccountService.reveal(account.id);
      setRevealed((current) => ({ ...current, [account.id]: number }));
    } catch (caught) {
      toast.error(caught, 'Could not reveal the account number.');
    }
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Banknote className="h-4 w-4 text-primary" aria-hidden />
          <CardTitle className="text-base">Bank accounts</CardTitle>
        </div>
        <CardDescription>
          Add more than one account to receive payment into - each can carry its own QR codes
          (GPay/PhonePe/Bank app). Pick which account + QR to print when creating an invoice.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {loading ? (
          <div className="flex justify-center py-6">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" aria-label="Loading" />
          </div>
        ) : accounts.length === 0 ? (
          <EmptyState icon={Banknote} title="No extra bank accounts yet"
                      description="The shop's single default account above is used until you add one here." />
        ) : (
          <div className="space-y-3">
            {accounts.map((account) => (
              <div key={account.id} className="rounded-md border p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="font-medium">{account.label}</p>
                      {account.defaultAccount ? (
                        <Badge variant="secondary"><Star className="mr-1 h-3 w-3" aria-hidden />Default</Badge>
                      ) : null}
                      <Badge variant="outline">{account.qrCodes.length} QR{account.qrCodes.length === 1 ? '' : 's'}</Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">{account.bankName} - {account.accountHolderName}</p>
                    <p className="mt-1 text-sm tabular">
                      {revealed[account.id] ?? account.accountNumberMasked} - {account.ifscCode}
                      {!revealed[account.id] ? (
                        <Button type="button" variant="ghost" size="sm" className="ml-1 h-6 px-1"
                                onClick={() => handleReveal(account)}>
                          <Eye className="h-3 w-3" aria-hidden />
                        </Button>
                      ) : null}
                    </p>
                    {account.upiId ? <p className="text-sm text-muted-foreground">UPI: {account.upiId}</p> : null}
                  </div>
                  <div className="flex gap-1">
                    <Button type="button" variant="outline" size="sm" onClick={() => setQrDialogAccount(account)}>
                      <QrCode className="h-4 w-4" />
                      QR codes
                    </Button>
                    <Button type="button" variant="ghost" size="icon" className="h-8 w-8"
                            aria-label={`Edit ${account.label}`} onClick={() => openEdit(account)}>
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button type="button" variant="ghost" size="icon" className="h-8 w-8 text-destructive hover:text-destructive"
                            aria-label={`Delete ${account.label}`} onClick={() => handleDelete(account)}>
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        <Button type="button" variant="outline" onClick={openCreate}>
          <Plus className="h-4 w-4" />
          Add bank account
        </Button>
      </CardContent>

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>{editing ? 'Edit bank account' : 'Add bank account'}</DialogTitle></DialogHeader>
          <form onSubmit={submit} noValidate className="space-y-4">
            {formError ? <Alert variant="destructive"><AlertDescription>{formError}</AlertDescription></Alert> : null}
            <FormField id="label" label="Label" error={errors.label?.message} required
                       hint='Your own name for it, e.g. "SBI Current Account".'>
              <Input id="label" {...register('label')} />
            </FormField>
            <FormField id="bankName" label="Bank name" error={errors.bankName?.message} required>
              <Input id="bankName" {...register('bankName')} />
            </FormField>
            <FormField id="accountHolderName" label="Account holder name" error={errors.accountHolderName?.message} required>
              <Input id="accountHolderName" {...register('accountHolderName')} />
            </FormField>
            <FormField id="accountNumber" label="Account number" error={errors.accountNumber?.message} required
                       hint={editing ? 'Leave the digits as shown, or click Reveal on the account first to edit them.' : undefined}>
              <Input id="accountNumber" inputMode="numeric" {...register('accountNumber')} />
            </FormField>
            <FormField id="ifscCode" label="IFSC code" error={errors.ifscCode?.message} required>
              <Input id="ifscCode" placeholder="HDFC0001234" className="uppercase" {...register('ifscCode')} />
            </FormField>
            <FormField id="upiId" label="UPI ID (optional)" error={errors.upiId?.message} hint="e.g. shopname@okicici">
              <Input id="upiId" {...register('upiId')} />
            </FormField>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={defaultAccount}
                        onCheckedChange={(checked) => setValue('defaultAccount', checked === true, { shouldDirty: true })} />
              Use as the default account for new invoices
            </label>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setFormOpen(false)} disabled={isSubmitting}>Cancel</Button>
              <Button type="submit" loading={isSubmitting}>{editing ? 'Save changes' : 'Add account'}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {qrDialogAccount ? (
        <QrCodesDialog
          account={qrDialogAccount}
          onClose={() => setQrDialogAccount(null)}
          onChanged={(updated) => {
            setAccounts((current) => current.map((a) => (a.id === updated.id ? updated : a)));
            setQrDialogAccount(updated);
          }}
        />
      ) : null}
    </Card>
  );
}

function QrCodesDialog({
  account, onClose, onChanged,
}: {
  account: TenantBankAccountResponse;
  onClose: () => void;
  onChanged: (updated: TenantBankAccountResponse) => void;
}) {
  const toast = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [label, setLabel] = useState('');
  const [busy, setBusy] = useState(false);

  const handleAdd = async (file: File) => {
    if (!label.trim()) {
      toast.error(new Error('Label required'), 'Give this QR a label first, e.g. "SBI QR" or "GPay".');
      return;
    }
    if (!QR_ACCEPTED.includes(file.type)) {
      toast.error(new Error('Unsupported format'), 'Use a PNG, JPEG or WebP image.');
      return;
    }
    if (file.size > QR_MAX_BYTES) {
      toast.error(new Error('Too large'), 'Image must be 2MB or smaller.');
      return;
    }
    setBusy(true);
    try {
      const updated = await tenantBankAccountService.addQr(account.id, label.trim(), file);
      onChanged(updated);
      setLabel('');
      toast.success('QR code added.');
    } catch (caught) {
      toast.error(caught, 'Could not upload the QR code.');
    } finally {
      setBusy(false);
    }
  };

  const handleRemove = async (qrId: number) => {
    setBusy(true);
    try {
      await tenantBankAccountService.removeQr(qrId);
      onChanged({ ...account, qrCodes: account.qrCodes.filter((qr) => qr.id !== qrId) });
      toast.success('QR code removed.');
    } catch (caught) {
      toast.error(caught, 'Could not remove the QR code.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>QR codes - {account.label}</DialogTitle></DialogHeader>
        <div className="space-y-4">
          {account.qrCodes.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No QR codes uploaded yet - invoices using this account will show account details only.
            </p>
          ) : (
            <div className="grid grid-cols-2 gap-3">
              {account.qrCodes.map((qr) => (
                <QrThumbnail key={qr.id} qrId={qr.id} label={qr.label} busy={busy} onRemove={() => handleRemove(qr.id)} />
              ))}
            </div>
          )}

          <div className="flex items-end gap-2 border-t pt-4">
            <FormField id="qr-label" label="New QR label" className="flex-1"
                       hint='e.g. "SBI QR", "GPay", "PhonePe"'>
              <Input id="qr-label" value={label} onChange={(event) => setLabel(event.target.value)} />
            </FormField>
            <input
              ref={inputRef}
              type="file"
              accept={QR_ACCEPTED.join(',')}
              className="hidden"
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) void handleAdd(file);
                event.target.value = '';
              }}
            />
            <Button type="button" variant="outline" disabled={busy} onClick={() => inputRef.current?.click()}>
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
              Upload
            </Button>
          </div>
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>Close</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function QrThumbnail({
  qrId, label, busy, onRemove,
}: {
  qrId: number;
  label: string;
  busy: boolean;
  onRemove: () => void;
}) {
  const src = useAuthenticatedImage(tenantBankAccountService.qrImageUrl(qrId));
  return (
    <div className="relative rounded-md border p-2">
      <Button type="button" variant="ghost" size="icon" className="absolute right-1 top-1 h-6 w-6"
              aria-label={`Remove ${label}`} disabled={busy} onClick={onRemove}>
        <X className="h-3 w-3" />
      </Button>
      <div className="flex h-20 items-center justify-center overflow-hidden rounded bg-muted">
        {src ? (
          <img src={src} alt={label} className="h-full w-full object-contain" />
        ) : (
          <QrCode className="h-6 w-6 text-muted-foreground" aria-hidden />
        )}
      </div>
      <p className="mt-1 truncate text-center text-xs text-muted-foreground">{label}</p>
    </div>
  );
}
