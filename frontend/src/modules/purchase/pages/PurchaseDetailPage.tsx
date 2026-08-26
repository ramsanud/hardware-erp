import { useCallback, useEffect, useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  ArrowLeft, Ban, FileDown, IndianRupee, Loader2, Paperclip,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Card, CardContent, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { formatDateTime } from '@/shared/lib/utils';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { PURCHASE_ROUTES, PAYMENT_METHOD_OPTIONS } from '../constants';
import { purchaseService } from '../services/purchaseService';
import { PurchaseStatusBadge } from '../components/PurchaseStatusBadge';
import type { PurchaseResponse } from '../types';

const paymentSchema = z.object({
  amountRupees: z.string().trim().min(1, 'Enter an amount').refine((v) => Number(v) > 0, 'Must be greater than zero'),
  paymentMethod: z.enum(['CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER']),
  notes: z.string().trim().max(255).optional().or(z.literal('')),
});
type PaymentFormValues = z.infer<typeof paymentSchema>;

function usePurchaseDetail(id: number) {
  const [purchase, setPurchase] = useState<PurchaseResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPurchase(await purchaseService.get(id));
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void reload(); }, [reload]);

  return { purchase, loading, error, reload };
}

export function PurchaseDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const navigate = useNavigate();
  const toast = useToast();
  const [payDialogOpen, setPayDialogOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  const { purchase, loading, error, reload } = usePurchaseDetail(id);

  const {
    register, control, handleSubmit, reset, formState: { errors, isSubmitting },
  } = useForm<PaymentFormValues>({
    resolver: zodResolver(paymentSchema),
    defaultValues: { amountRupees: '', paymentMethod: 'CASH', notes: '' },
  });

  if (Number.isNaN(id)) return <Navigate to={PURCHASE_ROUTES.list} replace />;

  const submitPayment = handleSubmit(async (values) => {
    try {
      await purchaseService.addPayment(id, {
        amountPaise: Math.round(Number(values.amountRupees) * 100),
        paymentMethod: values.paymentMethod,
        notes: values.notes || null,
      });
      toast.success('Payment recorded.');
      setPayDialogOpen(false);
      reset();
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not record this payment.');
    }
  });

  const handleCancel = async () => {
    try {
      await purchaseService.cancel(id);
      toast.success('Purchase cancelled and stock reversed.');
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not cancel this purchase.');
      throw caught;
    }
  };

  if (error) return <Card><ErrorState error={error} onRetry={reload} /></Card>;
  if (loading || !purchase) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  const canTakePayment = purchase.status === 'RECEIVED' || purchase.status === 'PARTIALLY_PAID';

  return (
    <>
      <PageHeader
        title={purchase.purchaseNumber}
        description={`${purchase.supplierName} · ${purchase.supplierMobile} · ${purchase.purchaseDate}`}
        actions={
          <div className="flex items-center gap-2">
            <Button variant="outline" onClick={() => navigate(PURCHASE_ROUTES.list)}>
              <ArrowLeft className="h-4 w-4" />Back
            </Button>
            {purchase.hasDocument ? (
              <Button variant="outline" asChild>
                <a href={purchaseService.documentUrl(id)} target="_blank" rel="noreferrer">
                  <FileDown className="h-4 w-4" />
                  <span className="hidden sm:inline">Original bill</span>
                </a>
              </Button>
            ) : null}
            {canTakePayment ? (
              <PermissionGate permission={PERMISSIONS.PURCHASE_MANAGE}>
                <Button onClick={() => setPayDialogOpen(true)}>
                  <IndianRupee className="h-4 w-4" />
                  Record payment
                </Button>
              </PermissionGate>
            ) : null}
            {purchase.status !== 'CANCELLED' ? (
              <PermissionGate permission={PERMISSIONS.PURCHASE_MANAGE}>
                <Button variant="outline" onClick={() => setCancelling(true)}>
                  <Ban className="h-4 w-4" />
                  Cancel
                </Button>
              </PermissionGate>
            ) : null}
          </div>
        }
      />

      {purchase.imported ? (
        <div className="mb-1 flex items-center gap-2 text-sm text-muted-foreground">
          <Paperclip className="h-3.5 w-3.5" aria-hidden />
          Imported from a supplier bill file{purchase.importedAt ? ` on ${formatDateTime(purchase.importedAt)}` : ''}.
        </div>
      ) : null}

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader><CardTitle className="text-base">Items</CardTitle></CardHeader>
          <CardContent className="px-0 pb-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead>Qty</TableHead>
                  <TableHead>Price</TableHead>
                  <TableHead className="text-right">Line total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {purchase.items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.productName}</TableCell>
                    <TableCell className="tabular">{item.quantity} {item.unit}</TableCell>
                    <TableCell className="tabular">₹{item.unitPriceDisplay}</TableCell>
                    <TableCell className="tabular text-right">₹{item.lineTotalDisplay}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <div className="space-y-5">
          <Card>
            <CardHeader><CardTitle className="text-base">Summary</CardTitle></CardHeader>
            <CardContent className="space-y-2 text-sm">
              {purchase.supplierBillNumber ? (
                <div className="flex justify-between"><span className="text-muted-foreground">Supplier bill #</span><span>{purchase.supplierBillNumber}</span></div>
              ) : null}
              <div className="flex justify-between"><span className="text-muted-foreground">Subtotal</span><span className="tabular">₹{purchase.subtotalDisplay}</span></div>
              <div className="flex justify-between"><span className="text-muted-foreground">GST</span><span className="tabular">₹{purchase.gstAmountDisplay}</span></div>
              <div className="flex justify-between font-semibold"><span>Total</span><span className="tabular">₹{purchase.totalDisplay}</span></div>
              <div className="flex justify-between"><span className="text-muted-foreground">Paid</span><span className="tabular">₹{purchase.paidDisplay}</span></div>
              <div className="flex justify-between font-semibold"><span>Balance</span><span className="tabular">₹{purchase.balanceDisplay}</span></div>
              <div className="flex items-center justify-between pt-1">
                <span className="text-muted-foreground">Status</span>
                <PurchaseStatusBadge status={purchase.status} />
              </div>
            </CardContent>
          </Card>

          {purchase.payments.length > 0 ? (
            <Card>
              <CardHeader><CardTitle className="text-base">Payments</CardTitle></CardHeader>
              <CardContent className="space-y-3 text-sm">
                {purchase.payments.map((payment) => (
                  <div key={payment.id} className="flex items-center justify-between">
                    <div>
                      <p>₹{payment.amountDisplay} · {payment.paymentMethod}</p>
                      <p className="text-xs text-muted-foreground">{formatDateTime(payment.paymentDate)}</p>
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>
          ) : null}
        </div>
      </div>

      <Dialog open={payDialogOpen} onOpenChange={(open) => { setPayDialogOpen(open); if (!open) reset(); }}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader><DialogTitle>Record a payment</DialogTitle></DialogHeader>
          <form onSubmit={submitPayment} className="space-y-4" noValidate>
            <p className="text-sm text-muted-foreground">Balance due: ₹{purchase.balanceDisplay}</p>
            <FormField id="amountRupees" label="Amount (₹)" error={errors.amountRupees?.message} required>
              <Input id="amountRupees" type="number" min={0.01} step="0.01" autoFocus
                     {...register('amountRupees')} />
            </FormField>
            <FormField id="paymentMethod" label="Payment method" error={errors.paymentMethod?.message} required>
              <Controller
                control={control}
                name="paymentMethod"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="paymentMethod"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {PAYMENT_METHOD_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </FormField>
            <FormField id="notes" label="Notes (optional)" error={errors.notes?.message}>
              <Input id="notes" {...register('notes')} />
            </FormField>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setPayDialogOpen(false)} disabled={isSubmitting}>
                Cancel
              </Button>
              <Button type="submit" loading={isSubmitting}>Record payment</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={cancelling}
        onOpenChange={setCancelling}
        title="Cancel this purchase?"
        description="Stock for every item on this purchase will be reversed. This cannot be undone."
        confirmLabel="Cancel purchase"
        destructive
        onConfirm={handleCancel}
      />
    </>
  );
}
