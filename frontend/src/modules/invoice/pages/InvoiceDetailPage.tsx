import { useCallback, useEffect, useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Ban, Download, Eye, IndianRupee, Loader2, Mail, MessageCircle, Pencil, Repeat, Share2,
} from 'lucide-react';
import { BackLink } from '@/shared/components/BackLink';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Card, CardContent, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/shared/components/ui/dropdown-menu';
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
import { downloadBlob, formatDateTime, previewBlob } from '@/shared/lib/utils';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { INVOICE_ROUTES, PAYMENT_METHOD_OPTIONS } from '../constants';
import { invoiceService } from '../services/invoiceService';
import { InvoiceStatusBadge } from '../components/InvoiceStatusBadge';
import type { InvoiceResponse } from '../types';

const paymentSchema = z.object({
  amountRupees: z.string().trim().min(1, 'Enter an amount').refine((v) => Number(v) > 0, 'Must be greater than zero'),
  paymentMethod: z.enum(['CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'OTHER']),
  notes: z.string().trim().max(255).optional().or(z.literal('')),
});
type PaymentFormValues = z.infer<typeof paymentSchema>;

function useInvoiceDetail(id: number) {
  const [invoice, setInvoice] = useState<InvoiceResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setInvoice(await invoiceService.get(id));
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void reload(); }, [reload]);

  return { invoice, loading, error, reload };
}

export function InvoiceDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const navigate = useNavigate();
  const toast = useToast();
  const [payDialogOpen, setPayDialogOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [downloadingPdf, setDownloadingPdf] = useState(false);
  const [previewingPdf, setPreviewingPdf] = useState(false);
  const [emailDialogOpen, setEmailDialogOpen] = useState(false);
  const [emailAddress, setEmailAddress] = useState('');
  const [emailing, setEmailing] = useState(false);
  const [sharingViaApp, setSharingViaApp] = useState(false);

  const { invoice, loading, error, reload } = useInvoiceDetail(id);

  const {
    register, control, handleSubmit, reset, formState: { errors, isSubmitting },
  } = useForm<PaymentFormValues>({
    resolver: zodResolver(paymentSchema),
    defaultValues: { amountRupees: '', paymentMethod: 'CASH', notes: '' },
  });

  if (Number.isNaN(id)) return <Navigate to={INVOICE_ROUTES.list} replace />;

  const submitPayment = handleSubmit(async (values) => {
    try {
      await invoiceService.addPayment(id, {
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

  const handleDownloadPdf = async () => {
    setDownloadingPdf(true);
    try {
      const blob = await invoiceService.pdf(id);
      downloadBlob(blob, `${invoice?.invoiceNumber ?? 'invoice'}.pdf`);
    } catch (caught) {
      toast.error(caught, 'Could not download the invoice PDF.');
    } finally {
      setDownloadingPdf(false);
    }
  };

  const handlePreviewPdf = async () => {
    setPreviewingPdf(true);
    try {
      const blob = await invoiceService.pdf(id);
      previewBlob(blob);
    } catch (caught) {
      toast.error(caught, 'Could not open the invoice PDF.');
    } finally {
      setPreviewingPdf(false);
    }
  };

  /** CR-036 - real SMTP send with the PDF attached; LOGGED_ONLY is not an error, just an honest "no mail server configured here" state. */
  const handleEmailShare = async () => {
    if (!emailAddress.trim()) return;
    setEmailing(true);
    try {
      const status = await invoiceService.emailInvoice(id, emailAddress.trim());
      if (status === 'SENT') {
        toast.success(`Invoice emailed to ${emailAddress.trim()}.`);
        setEmailDialogOpen(false);
        setEmailAddress('');
      } else if (status === 'LOGGED_ONLY') {
        toast.info('Email is not configured on this server yet - nothing was actually sent.');
      } else {
        toast.error(new Error('Send failed'), 'Could not send the email. Please try again.');
      }
    } catch (caught) {
      toast.error(caught, 'Could not send the email.');
    } finally {
      setEmailing(false);
    }
  };

  /**
   * No WhatsApp Business API is configured anywhere in this app (there is no
   * real provider credential to send through) - this uses the browser's own
   * Web Share API, which hands the actual PDF to whatever the device offers
   * (WhatsApp included, on a phone or a browser that supports file sharing).
   * Falls back to opening WhatsApp's own web link with a text summary (no
   * attachment - a URL can't carry a file) when the browser can't share files,
   * telling the user plainly rather than pretending it attached the PDF.
   */
  const handleShareViaApp = async () => {
    if (!invoice) return;
    setSharingViaApp(true);
    try {
      const blob = await invoiceService.pdf(id);
      const file = new File([blob], `${invoice.invoiceNumber}.pdf`, { type: 'application/pdf' });
      const shareData = { files: [file], title: `Invoice ${invoice.invoiceNumber}`, text: `Invoice ${invoice.invoiceNumber} - ${invoice.totalDisplay}` };
      if (navigator.canShare?.(shareData)) {
        await navigator.share(shareData);
        return;
      }
      downloadBlob(blob, `${invoice.invoiceNumber}.pdf`);
      const text = encodeURIComponent(`Invoice ${invoice.invoiceNumber} for ${invoice.totalDisplay} - I've downloaded the PDF, attaching it here.`);
      window.open(`https://wa.me/?text=${text}`, '_blank', 'noopener,noreferrer');
      toast.info('The PDF was downloaded - attach it manually in the WhatsApp chat that just opened.');
    } catch (caught) {
      if (caught instanceof DOMException && caught.name === 'AbortError') return;
      toast.error(caught, 'Could not share the invoice.');
    } finally {
      setSharingViaApp(false);
    }
  };

  const handleCancel = async () => {
    try {
      await invoiceService.cancel(id);
      toast.success('Invoice cancelled and stock restored.');
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not cancel this invoice.');
      throw caught;
    }
  };

  /** CR-030 §45 - same customer and quantities, current prices. Never carries the old price forward. */
  const handleRepeat = () => {
    if (!invoice) return;
    navigate(INVOICE_ROUTES.create, {
      state: {
        customer: { customerName: invoice.customerName, customerMobile: invoice.customerMobile },
        items: invoice.items.map((item) => ({ productId: item.productId, quantity: item.quantity })),
      },
    });
  };

  /**
   * Amend this invoice instead of rebuilding it - the "customer wants one more
   * item before leaving" case. Only offered while nothing has been paid: the
   * server refuses otherwise, and showing a button that always fails would be
   * worse than not showing one.
   */
  // Mirrors the server's own guard: UNPAID with no payment rows. Kept as two
  // checks rather than trusting status alone, because status is derived and a
  // zero-value payment row would still make the server refuse.
  const canEdit = Boolean(invoice)
    && invoice!.status === 'UNPAID'
    && (invoice!.payments?.length ?? 0) === 0;

  const handleEdit = () => {
    if (!invoice) return;
    navigate(INVOICE_ROUTES.create, {
      state: {
        editInvoiceId: invoice.id,
        editInvoiceNumber: invoice.invoiceNumber,
        customer: {
          customerName: invoice.customerName,
          customerMobile: invoice.customerMobile,
        },
        items: invoice.items.map((item) => ({ productId: item.productId, quantity: item.quantity })),
      },
    });
  };

  if (error) return <Card><ErrorState error={error} onRetry={reload} /></Card>;
  if (loading || !invoice) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  const canTakePayment = invoice.status === 'UNPAID' || invoice.status === 'PARTIALLY_PAID';

  return (
    <>
      <BackLink to={INVOICE_ROUTES.list} label="Invoices" />

      <PageHeader
        title={invoice.invoiceNumber}
        description={`${invoice.customerName} · ${invoice.customerMobile} · ${invoice.invoiceDate}`}
        actions={
          <div className="flex items-center gap-2">
            <Button variant="outline" onClick={handlePreviewPdf} loading={previewingPdf}>
              <Eye className="h-4 w-4" />
              <span className="hidden sm:inline">Preview</span>
            </Button>
            <Button variant="outline" onClick={handleDownloadPdf} loading={downloadingPdf}>
              <Download className="h-4 w-4" />
              <span className="hidden sm:inline">Download PDF</span>
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline">
                  <Share2 className="h-4 w-4" />
                  <span className="hidden sm:inline">Share</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onSelect={() => setEmailDialogOpen(true)}>
                  <Mail className="h-4 w-4" />
                  Email
                </DropdownMenuItem>
                <DropdownMenuItem disabled={sharingViaApp} onSelect={() => void handleShareViaApp()}>
                  <MessageCircle className="h-4 w-4" />
                  WhatsApp / more apps
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            {canEdit ? (
              <PermissionGate permission={PERMISSIONS.INVOICE_CREATE}>
                <Button variant="outline" onClick={handleEdit}>
                  <Pencil className="h-4 w-4" />
                  <span className="hidden sm:inline">Edit</span>
                </Button>
              </PermissionGate>
            ) : null}
            <PermissionGate permission={PERMISSIONS.INVOICE_CREATE}>
              <Button variant="outline" onClick={handleRepeat}>
                <Repeat className="h-4 w-4" />
                <span className="hidden sm:inline">Repeat</span>
              </Button>
            </PermissionGate>
            {canTakePayment ? (
              <PermissionGate permission={PERMISSIONS.PAYMENT_MANAGE}>
                <Button onClick={() => setPayDialogOpen(true)}>
                  <IndianRupee className="h-4 w-4" />
                  Record payment
                </Button>
              </PermissionGate>
            ) : null}
            {invoice.status !== 'CANCELLED' ? (
              <PermissionGate permission={PERMISSIONS.INVOICE_CANCEL}>
                <Button variant="outline" onClick={() => setCancelling(true)}>
                  <Ban className="h-4 w-4" />
                  Cancel
                </Button>
              </PermissionGate>
            ) : null}
          </div>
        }
      />

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
                {invoice.items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.productName}</TableCell>
                    <TableCell className="tabular">{item.quantity}</TableCell>
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
              <div className="flex justify-between"><span className="text-muted-foreground">Subtotal</span><span className="tabular">₹{invoice.subtotalDisplay}</span></div>
              {invoice.discountDisplay ? (
                <div className="flex justify-between text-emerald-600 dark:text-emerald-400">
                  <span>Discount {invoice.couponCode ? `(${invoice.couponCode})` : ''}</span>
                  <span className="tabular">-₹{invoice.discountDisplay}</span>
                </div>
              ) : null}
              <div className="flex justify-between"><span className="text-muted-foreground">GST</span><span className="tabular">₹{invoice.gstAmountDisplay}</span></div>
              <div className="flex justify-between font-semibold"><span>Total</span><span className="tabular">₹{invoice.totalDisplay}</span></div>
              <div className="flex justify-between"><span className="text-muted-foreground">Paid</span><span className="tabular">₹{invoice.paidDisplay}</span></div>
              <div className="flex justify-between font-semibold"><span>Balance</span><span className="tabular">₹{invoice.balanceDisplay}</span></div>
              <div className="flex items-center justify-between pt-1">
                <span className="text-muted-foreground">Status</span>
                <InvoiceStatusBadge status={invoice.status} />
              </div>
            </CardContent>
          </Card>

          {invoice.payments.length > 0 ? (
            <Card>
              <CardHeader><CardTitle className="text-base">Payments</CardTitle></CardHeader>
              <CardContent className="space-y-3 text-sm">
                {invoice.payments.map((payment) => (
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
            <p className="text-sm text-muted-foreground">Balance due: ₹{invoice.balanceDisplay}</p>
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

      <Dialog open={emailDialogOpen} onOpenChange={(open) => { setEmailDialogOpen(open); if (!open) setEmailAddress(''); }}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader><DialogTitle>Email this invoice</DialogTitle></DialogHeader>
          <FormField id="shareEmail" label="Send to" required>
            <Input id="shareEmail" type="email" autoFocus placeholder="customer@example.com"
                   value={emailAddress} onChange={(event) => setEmailAddress(event.target.value)} />
          </FormField>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setEmailDialogOpen(false)} disabled={emailing}>Cancel</Button>
            <Button type="button" onClick={() => void handleEmailShare()} loading={emailing} disabled={!emailAddress.trim()}>
              <Mail className="h-4 w-4" />
              Send
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={cancelling}
        onOpenChange={setCancelling}
        title="Cancel this invoice?"
        description="Stock for every item on this invoice will be restored. This cannot be undone."
        confirmLabel="Cancel invoice"
        destructive
        onConfirm={handleCancel}
      />
    </>
  );
}
