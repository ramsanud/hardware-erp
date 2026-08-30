import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft, ArrowRightCircle, Download, Eye, Loader2, Pencil, Repeat, Send, ThumbsDown, ThumbsUp,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { ApiError } from '@/shared/types/api';
import { downloadBlob, previewBlob } from '@/shared/lib/utils';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { INVOICE_ROUTES } from '@/modules/invoice/constants';
import { QUOTATION_ROUTES } from '../constants';
import { quotationService } from '../services/quotationService';
import { QuotationStatusBadge } from '../components/QuotationStatusBadge';
import type { QuotationResponse } from '../types';

function useQuotationDetail(id: number) {
  const [quotation, setQuotation] = useState<QuotationResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setQuotation(await quotationService.get(id));
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void reload(); }, [reload]);

  return { quotation, loading, error, reload };
}

export function QuotationDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const navigate = useNavigate();
  const toast = useToast();
  const [converting, setConverting] = useState(false);
  const [busy, setBusy] = useState(false);
  const [previewingPdf, setPreviewingPdf] = useState(false);
  const [downloadingPdf, setDownloadingPdf] = useState(false);

  const { quotation, loading, error, reload } = useQuotationDetail(id);

  if (Number.isNaN(id)) return <Navigate to={QUOTATION_ROUTES.list} replace />;

  const setStatus = async (status: 'SENT' | 'ACCEPTED' | 'REJECTED') => {
    setBusy(true);
    try {
      await quotationService.updateStatus(id, status);
      toast.success('Quotation status updated.');
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not update the quotation status.');
    } finally {
      setBusy(false);
    }
  };

  const handlePreviewPdf = async () => {
    setPreviewingPdf(true);
    try {
      previewBlob(await quotationService.pdf(id));
    } catch (caught) {
      toast.error(caught, 'Could not open the quotation PDF.');
    } finally {
      setPreviewingPdf(false);
    }
  };

  const handleDownloadPdf = async () => {
    setDownloadingPdf(true);
    try {
      const blob = await quotationService.pdf(id);
      downloadBlob(blob, `${quotation?.quotationNumber ?? 'quotation'}.pdf`);
    } catch (caught) {
      toast.error(caught, 'Could not download the quotation PDF.');
    } finally {
      setDownloadingPdf(false);
    }
  };

  const handleConvert = async () => {
    try {
      const updated = await quotationService.convert(id);
      toast.success(`Converted to invoice.`);
      if (updated.convertedInvoiceId) {
        navigate(INVOICE_ROUTES.detail(updated.convertedInvoiceId));
      } else {
        await reload();
      }
    } catch (caught) {
      toast.error(caught, 'Could not convert this quotation to an invoice.');
      throw caught;
    }
  };

  /** CR-030 §46 - same customer and quantities, current prices. */
  const handleRepeat = () => {
    if (!quotation) return;
    navigate(QUOTATION_ROUTES.create, {
      state: {
        customer: { customerName: quotation.customerName, customerMobile: quotation.customerMobile },
        items: quotation.items.map((item) => ({ productId: item.productId, quantity: item.quantity })),
      },
    });
  };

  /**
   * Edit in place rather than rebuilding. Only DRAFT and SENT qualify -
   * ACCEPTED figures are ones the customer has agreed to, and CONVERTED means
   * an invoice already exists against these lines. The server enforces the
   * same rule; this just avoids offering a button that would fail.
   */
  const canEdit = Boolean(quotation)
    && (quotation!.status === 'DRAFT' || quotation!.status === 'SENT');

  const handleEdit = () => {
    if (!quotation) return;
    navigate(QUOTATION_ROUTES.create, {
      state: {
        editQuotationId: quotation.id,
        editQuotationNumber: quotation.quotationNumber,
        customer: { customerName: quotation.customerName, customerMobile: quotation.customerMobile },
        items: quotation.items.map((item) => ({ productId: item.productId, quantity: item.quantity })),
      },
    });
  };

  if (error) return <Card><ErrorState error={error} onRetry={reload} /></Card>;
  if (loading || !quotation) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  const canConvert = (quotation.status === 'DRAFT' || quotation.status === 'SENT'
    || quotation.status === 'ACCEPTED') && !quotation.expired;
  const canMarkSent = quotation.status === 'DRAFT';
  const canDecide = quotation.status === 'DRAFT' || quotation.status === 'SENT';

  return (
    <>
      <PageHeader
        title={quotation.quotationNumber}
        description={`${quotation.customerName} · ${quotation.customerMobile} · valid until ${quotation.validUntil}`}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="outline" asChild>
              <Link to={QUOTATION_ROUTES.list}><ArrowLeft className="h-4 w-4" />Back</Link>
            </Button>
            <Button variant="outline" onClick={handlePreviewPdf} loading={previewingPdf}>
              <Eye className="h-4 w-4" />
              <span className="hidden sm:inline">Preview</span>
            </Button>
            <Button variant="outline" onClick={handleDownloadPdf} loading={downloadingPdf}>
              <Download className="h-4 w-4" />
              <span className="hidden sm:inline">Download PDF</span>
            </Button>
            {canEdit ? (
              <PermissionGate permission={PERMISSIONS.QUOTATION_MANAGE}>
                <Button variant="outline" onClick={handleEdit}>
                  <Pencil className="h-4 w-4" />
                  <span className="hidden sm:inline">Edit</span>
                </Button>
              </PermissionGate>
            ) : null}
            <PermissionGate permission={PERMISSIONS.QUOTATION_MANAGE}>
              <Button variant="outline" onClick={handleRepeat}>
                <Repeat className="h-4 w-4" />
                <span className="hidden sm:inline">Repeat</span>
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.QUOTATION_MANAGE}>
              {canMarkSent ? (
                <Button variant="outline" onClick={() => setStatus('SENT')} disabled={busy}>
                  <Send className="h-4 w-4" />Mark sent
                </Button>
              ) : null}
              {canDecide ? (
                <>
                  <Button variant="outline" onClick={() => setStatus('ACCEPTED')} disabled={busy}>
                    <ThumbsUp className="h-4 w-4" />Accepted
                  </Button>
                  <Button variant="outline" onClick={() => setStatus('REJECTED')} disabled={busy}>
                    <ThumbsDown className="h-4 w-4" />Rejected
                  </Button>
                </>
              ) : null}
            </PermissionGate>
            {canConvert ? (
              <PermissionGate permission={PERMISSIONS.INVOICE_CREATE}>
                <Button onClick={() => setConverting(true)}>
                  <ArrowRightCircle className="h-4 w-4" />Convert to invoice
                </Button>
              </PermissionGate>
            ) : null}
          </div>
        }
      />

      {quotation.expired && quotation.status !== 'CONVERTED' && quotation.status !== 'REJECTED' ? (
        <div className="rounded-md border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          This quotation expired on {quotation.validUntil} and can no longer be converted. Create a new one to re-quote.
        </div>
      ) : null}

      {quotation.status === 'CONVERTED' && quotation.convertedInvoiceId ? (
        <div className="rounded-md border border-success/30 bg-success/10 px-4 py-3 text-sm">
          Converted to{' '}
          <Link to={INVOICE_ROUTES.detail(quotation.convertedInvoiceId)} className="font-medium underline">
            invoice
          </Link>.
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
                  <TableHead className="text-right">Qty</TableHead>
                  <TableHead className="text-right">Price</TableHead>
                  <TableHead className="text-right">Discount</TableHead>
                  <TableHead className="text-right">Line total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {quotation.items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.productName}</TableCell>
                    <TableCell className="tabular text-right">{item.quantity}</TableCell>
                    <TableCell className="tabular text-right">₹{item.unitPriceDisplay}</TableCell>
                    <TableCell className="tabular text-right">
                      {item.discountType === 'NONE' ? (
                        <span className="text-muted-foreground">—</span>
                      ) : (
                        <span className="text-destructive">
                          {item.discountType === 'PERCENTAGE'
                            ? `${Number(item.discountPercent)}%`
                            : `₹${item.discountDisplay}`}
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="tabular text-right">
                      {/* Struck-through gross beside the net, so the customer can
                          see what the discount was taken off - the whole point of
                          a quotation they will compare against another shop. */}
                      {item.discountType !== 'NONE' ? (
                        <span className="mr-1.5 text-xs text-muted-foreground line-through">
                          ₹{item.lineGrossDisplay}
                        </span>
                      ) : null}
                      ₹{item.lineTotalDisplay}
                    </TableCell>
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
              {/*
                CR-049 ladder. Before this the card showed only Subtotal / GST /
                Total, so a quotation discounted to zero rendered three ₹0.00
                rows and read as broken data rather than as a 100% discount.
                Each discount row appears only when that discount is non-null.
              */}
              <div className="flex justify-between">
                <span className="text-muted-foreground">Subtotal</span>
                <span className="tabular">₹{quotation.grossSubtotalDisplay}</span>
              </div>
              {quotation.productDiscountDisplay ? (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Product discounts</span>
                  <span className="tabular text-destructive">-₹{quotation.productDiscountDisplay}</span>
                </div>
              ) : null}
              {quotation.quotationDiscountDisplay ? (
                <>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">After product discounts</span>
                    <span className="tabular">₹{quotation.afterProductDiscountDisplay}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">
                      Quotation discount
                      {quotation.quotationDiscountType === 'PERCENTAGE'
                        ? ` (${Number(quotation.quotationDiscountPercent)}%)` : ''}
                    </span>
                    <span className="tabular text-destructive">-₹{quotation.quotationDiscountDisplay}</span>
                  </div>
                </>
              ) : null}
              <div className="flex justify-between">
                <span className="text-muted-foreground">Taxable amount</span>
                <span className="tabular">₹{quotation.subtotalDisplay}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">GST</span>
                <span className="tabular">₹{quotation.gstAmountDisplay}</span>
              </div>
              <div className="flex justify-between border-t pt-2 text-base font-semibold">
                <span>Total</span><span className="tabular">₹{quotation.totalDisplay}</span>
              </div>
              {quotation.totalSavingsDisplay ? (
                <div className="rounded-md bg-primary/10 px-2.5 py-1.5 text-center text-xs font-medium text-primary">
                  You save ₹{quotation.totalSavingsDisplay}
                </div>
              ) : null}
              <div className="flex items-center justify-between pt-1">
                <span className="text-muted-foreground">Status</span>
                <QuotationStatusBadge status={quotation.status} expired={quotation.expired} />
              </div>
            </CardContent>
          </Card>

          {quotation.remarks ? (
            <Card>
              <CardHeader><CardTitle className="text-base">Remarks</CardTitle></CardHeader>
              <CardContent className="text-sm">{quotation.remarks}</CardContent>
            </Card>
          ) : null}
        </div>
      </div>

      <ConfirmDialog
        open={converting}
        onOpenChange={setConverting}
        title="Convert this quotation to an invoice?"
        description="Stock will be decremented and a real invoice will be created, priced at each product's current rate (not the price shown on this quote)."
        confirmLabel="Convert"
        onConfirm={handleConvert}
      />
    </>
  );
}
