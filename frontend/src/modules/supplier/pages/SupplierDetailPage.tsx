import { useCallback, useEffect, useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import {
  Eye, EyeOff, Loader2, Trash2, UserPlus,
} from 'lucide-react';
import { BackLink } from '@/shared/components/BackLink';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { ApiError } from '@/shared/types/api';
import { formatDateTime } from '@/shared/lib/utils';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { SUPPLIER_ROUTES } from '../constants';
import { supplierService } from '../services/supplierService';
import { ContactForm } from '../forms/ContactForm';
import { ContactList } from '../components/ContactList';
import { SupplierStatusBadge } from '../components/SupplierStatusBadge';
import type { SupplierResponse, SupplierContactResponse } from '../types';
import type { SupplierContactValues } from '../validation/schemas';

function useSupplierDetail(id: number) {
  const [supplier, setSupplier] = useState<SupplierResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSupplier(await supplierService.get(id));
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void reload(); }, [reload]);

  return { supplier, loading, error, reload };
}

export function SupplierDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const navigate = useNavigate();
  const toast = useToast();

  const [deleting, setDeleting] = useState(false);
  const [addingContact, setAddingContact] = useState(false);
  const [editingContact, setEditingContact] = useState<SupplierContactResponse | null>(null);
  const [removingContact, setRemovingContact] = useState<SupplierContactResponse | null>(null);
  const [revealedBankAccountNo, setRevealedBankAccountNo] = useState<string | null>(null);
  const [revealingBankAccount, setRevealingBankAccount] = useState(false);

  const validId = Boolean(params.id) && !Number.isNaN(id);
  const { supplier, loading, error, reload } = useSupplierDetail(validId ? id : -1);

  if (!validId) {
    return <Navigate to={SUPPLIER_ROUTES.list} replace />;
  }

  if (loading) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  if (error || !supplier) {
    return (
      <ErrorState
        error={error ?? new ApiError({ message: 'Supplier not found', code: 'NOT_FOUND', status: 404 })}
        onRetry={reload}
      />
    );
  }

  const handleToggleBankAccountReveal = async () => {
    if (revealedBankAccountNo) {
      // Toggling closed never keeps the decrypted value in state longer than the toggle is open (CR-018).
      setRevealedBankAccountNo(null);
      return;
    }
    setRevealingBankAccount(true);
    try {
      setRevealedBankAccountNo(await supplierService.revealBankAccountNumber(supplier.id));
    } catch (caught) {
      toast.error(caught, 'Could not reveal the bank account number.');
    } finally {
      setRevealingBankAccount(false);
    }
  };

  const handleDeactivate = async () => {
    try {
      await supplierService.remove(supplier.id);
      toast.success(`${supplier.supplierName} has been deactivated.`);
      navigate(SUPPLIER_ROUTES.list);
    } catch (caught) {
      toast.error(caught, 'Could not deactivate this supplier.');
      throw caught;
    }
  };

  const handleAddContact = async (values: SupplierContactValues) => {
    await supplierService.addContact(supplier.id, values);
    setAddingContact(false);
    toast.success('Contact added.');
    await reload();
  };

  const handleUpdateContact = async (values: SupplierContactValues) => {
    if (!editingContact) return;
    await supplierService.updateContact(supplier.id, editingContact.id, values);
    setEditingContact(null);
    toast.success('Contact updated.');
    await reload();
  };

  const handleRemoveContact = async () => {
    if (!removingContact) return;
    await supplierService.removeContact(supplier.id, removingContact.id);
    toast.success('Contact removed.');
    await reload();
  };

  return (
    <>
      <BackLink to={SUPPLIER_ROUTES.list} label="Suppliers" />

      <PageHeader
        title={supplier.supplierName}
        description={supplier.supplierCode}
        actions={
          <PermissionGate permission={PERMISSIONS.SUPPLIER_MANAGE}>
            <div className="flex items-center gap-2">
              <Button variant="outline" onClick={() => navigate(SUPPLIER_ROUTES.edit(supplier.id))}>Edit</Button>
              <Button variant="outline" className="text-destructive hover:text-destructive"
                      onClick={() => setDeleting(true)}>
                <Trash2 className="h-4 w-4" />
                <span className="hidden sm:inline">Deactivate</span>
              </Button>
            </div>
          </PermissionGate>
        }
      />

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="text-base">Overview</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Status</span>
              <SupplierStatusBadge status={supplier.status} />
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Contact Person Name</span>
              <span>{supplier.contactPerson ?? '—'}</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Mobile</span>
              <span className="tabular">{supplier.mobileNo}</span>
            </div>
            {supplier.alternateMobileNo ? (
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Alternate mobile</span>
                <span className="tabular">{supplier.alternateMobileNo}</span>
              </div>
            ) : null}
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Email</span>
              <span className="truncate">{supplier.email ?? '—'}</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Payment terms</span>
              <span>{supplier.paymentTermsDays} days</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Credit limit</span>
              <span className="tabular">₹{supplier.creditLimitDisplay}</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">Created</span>
              <span className="tabular text-right">{formatDateTime(supplier.createdAt)}</span>
            </div>
          </CardContent>
        </Card>

        <div className="space-y-5 lg:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Address & tax</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
              <div>
                <p className="text-muted-foreground">Address</p>
                <p>{supplier.addressLine1 ?? '—'}</p>
                {supplier.addressLine2 ? <p>{supplier.addressLine2}</p> : null}
                <p>
                  {[supplier.city, supplier.pincode].filter(Boolean).join(' · ') || '—'}
                </p>
              </div>
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">GSTIN</span>
                  <span className="tabular">{supplier.gstNo ?? '—'}</span>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">PAN</span>
                  <span className="tabular">{supplier.panNo ?? '—'}</span>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">State code</span>
                  <span className="tabular">{supplier.stateCode ?? '—'}</span>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Bank details</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Account name</span>
                <span>{supplier.bankAccountName ?? '—'}</span>
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Account number</span>
                <span className="flex items-center gap-1.5">
                  <span className="tabular">{revealedBankAccountNo ?? supplier.bankAccountNo ?? '—'}</span>
                  {supplier.bankAccountNo ? (
                    <PermissionGate permission={PERMISSIONS.SUPPLIER_VIEW_BANK_ACCOUNT}>
                      <button
                        type="button"
                        onClick={() => void handleToggleBankAccountReveal()}
                        disabled={revealingBankAccount}
                        className="rounded-sm p-1 text-muted-foreground hover:text-foreground disabled:opacity-50"
                        aria-label={revealedBankAccountNo ? 'Hide full account number' : 'Reveal full account number'}
                      >
                        {revealingBankAccount ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        ) : revealedBankAccountNo ? (
                          <EyeOff className="h-3.5 w-3.5" />
                        ) : (
                          <Eye className="h-3.5 w-3.5" />
                        )}
                      </button>
                    </PermissionGate>
                  ) : null}
                </span>
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">IFSC</span>
                <span className="tabular">{supplier.bankIfsc ?? '—'}</span>
              </div>
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Bank name</span>
                <span>{supplier.bankName ?? '—'}</span>
              </div>
            </CardContent>
          </Card>

          {supplier.remarks ? (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Remarks</CardTitle>
              </CardHeader>
              <CardContent className="text-sm">{supplier.remarks}</CardContent>
            </Card>
          ) : null}

          <Card>
            <CardHeader className="flex-row items-center justify-between space-y-0">
              <CardTitle className="text-base">Contacts</CardTitle>
              <PermissionGate permission={PERMISSIONS.SUPPLIER_MANAGE}>
                <Button variant="outline" size="sm" onClick={() => setAddingContact(true)}>
                  <UserPlus className="h-4 w-4" />
                  Add contact
                </Button>
              </PermissionGate>
            </CardHeader>
            <CardContent>
              <ContactList
                contacts={supplier.contacts}
                onEdit={setEditingContact}
                onDelete={setRemovingContact}
              />
            </CardContent>
          </Card>
        </div>
      </div>

      <Dialog open={addingContact} onOpenChange={setAddingContact}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>Add contact</DialogTitle></DialogHeader>
          <ContactForm onSubmit={handleAddContact} onCancel={() => setAddingContact(false)} />
        </DialogContent>
      </Dialog>

      <Dialog open={editingContact !== null} onOpenChange={(open) => !open && setEditingContact(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>Edit contact</DialogTitle></DialogHeader>
          {editingContact ? (
            <ContactForm contact={editingContact} onSubmit={handleUpdateContact}
                        onCancel={() => setEditingContact(null)} />
          ) : null}
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title="Deactivate this supplier?"
        description={`${supplier.supplierName} will no longer appear when raising a purchase order. Past purchase history is retained.`}
        confirmLabel="Deactivate"
        destructive
        onConfirm={handleDeactivate}
      />

      <ConfirmDialog
        open={removingContact !== null}
        onOpenChange={(open) => !open && setRemovingContact(null)}
        title="Remove this contact?"
        description={`${removingContact?.contactName ?? 'This contact'} will be permanently removed. Contacts carry no financial history.`}
        confirmLabel="Remove"
        destructive
        onConfirm={handleRemoveContact}
      />
    </>
  );
}
