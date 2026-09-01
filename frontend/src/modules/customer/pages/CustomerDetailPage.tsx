import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import {
  ClipboardList, FileText, IndianRupee, Loader2, Pencil, Plus,
} from 'lucide-react';
import { BackLink } from '@/shared/components/BackLink';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Tabs, TabsContent, TabsList, TabsTrigger,
} from '@/shared/components/ui/tabs';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { EmptyState } from '@/shared/components/EmptyState';
import { UnsavedChangesDialog } from '@/shared/components/UnsavedChangesDialog';
import { ApiError } from '@/shared/types/api';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { INVOICE_ROUTES } from '@/modules/invoice/constants';
import { InvoiceStatusBadge } from '@/modules/invoice/components/InvoiceStatusBadge';
import { QUOTATION_ROUTES } from '@/modules/quotation/constants';
import { QuotationStatusBadge } from '@/modules/quotation/components/QuotationStatusBadge';
import { CUSTOMER_ROUTES } from '../constants';
import { customerService } from '../services/customerService';
import { CustomerForm, CUSTOMER_FORM_ID } from '../forms/CustomerForm';
import type {
  CustomerFinancialSummaryResponse, CustomerProductHistoryResponse, CustomerRequest, CustomerResponse,
} from '../types';
import type { InvoiceSummaryResponse } from '@/modules/invoice/types';
import type { QuotationSummaryResponse } from '@/modules/quotation/types';
import type { PageResponse } from '@/shared/types/api';

/**
 * Customer 360 (CR-030): the customer's full history in one place - not
 * separate unrelated pages - so "who bought what, when, for how much" is
 * always one click away, and starting a new document for a repeat
 * customer never means re-typing their details.
 */
function useCustomerDetail(id: number) {
  const [customer, setCustomer] = useState<CustomerResponse | null>(null);
  const [summary, setSummary] = useState<CustomerFinancialSummaryResponse | null>(null);
  const [invoices, setInvoices] = useState<PageResponse<InvoiceSummaryResponse> | null>(null);
  const [quotations, setQuotations] = useState<PageResponse<QuotationSummaryResponse> | null>(null);
  const [products, setProducts] = useState<CustomerProductHistoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [c, s, i, q, p] = await Promise.all([
        customerService.get(id),
        customerService.financialSummary(id),
        customerService.recentInvoices(id, 10),
        customerService.recentQuotations(id, 10),
        customerService.productHistory(id),
      ]);
      setCustomer(c);
      setSummary(s);
      setInvoices(i);
      setQuotations(q);
      setProducts(p);
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void reload(); }, [reload]);

  return {
    customer, summary, invoices, quotations, products, loading, error, reload,
  };
}

export function CustomerDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const navigate = useNavigate();
  const toast = useToast();
  const [editMode, setEditMode] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [confirmingClose, setConfirmingClose] = useState(false);

  const validId = Boolean(params.id) && !Number.isNaN(id);
  const {
    customer, summary, invoices, quotations, products, loading, error, reload,
  } = useCustomerDetail(validId ? id : -1);

  if (!validId) return <Navigate to={CUSTOMER_ROUTES.list} replace />;

  const requestExitEditMode = () => {
    if (dirty) { setConfirmingClose(true); return; }
    setEditMode(false);
  };

  const handleSave = async (request: CustomerRequest) => {
    const updated = await customerService.update(id, request);
    toast.success(`${updated.customerName} updated.`);
    setDirty(false);
    setConfirmingClose(false);
    setEditMode(false);
    await reload();
  };

  if (error) return <Card><ErrorState error={error} onRetry={reload} /></Card>;
  if (loading || !customer || !summary) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  // Never lock a re-typed value - just pre-fill so the wizard's Customer
  // step opens already-populated, still fully editable (CR-030 §10-11).
  const goToNewQuotation = () => navigate(QUOTATION_ROUTES.create, {
    state: {
      customer: {
        customerName: customer.customerName, customerMobile: customer.mobileNo,
        customerEmail: customer.email, customerGstNo: customer.gstNo, customerStateCode: customer.stateCode,
      },
    },
  });
  const goToNewInvoice = () => navigate(INVOICE_ROUTES.create, {
    state: {
      customer: {
        customerName: customer.customerName, customerMobile: customer.mobileNo,
        customerEmail: customer.email, customerGstNo: customer.gstNo, customerStateCode: customer.stateCode,
      },
    },
  });

  return (
    <>
      <BackLink to={CUSTOMER_ROUTES.list} label="Customers" />

      <PageHeader
        title={customer.customerName}
        description={`${customer.customerCode} · ${customer.mobileNo}`}
        actions={
          !editMode ? (
            <div className="flex flex-wrap items-center gap-2">
              <PermissionGate permission={PERMISSIONS.QUOTATION_MANAGE}>
                <Button variant="outline" onClick={goToNewQuotation}>
                  <ClipboardList className="h-4 w-4" /> <span className="hidden sm:inline">New quotation</span>
                </Button>
              </PermissionGate>
              <PermissionGate permission={PERMISSIONS.INVOICE_CREATE}>
                <Button variant="outline" onClick={goToNewInvoice}>
                  <FileText className="h-4 w-4" /> <span className="hidden sm:inline">New invoice</span>
                </Button>
              </PermissionGate>
              <PermissionGate permission={PERMISSIONS.CUSTOMER_MANAGE}>
                <Button variant="outline" onClick={() => setEditMode(true)}>
                  <Pencil className="h-4 w-4" /> Edit
                </Button>
              </PermissionGate>
            </div>
          ) : undefined
        }
      />

      {editMode ? (
        <Card>
          <CardHeader><CardTitle className="text-base">Edit customer</CardTitle></CardHeader>
          <CardContent>
            <CustomerForm
              customer={customer}
              onSubmit={handleSave}
              onCancel={requestExitEditMode}
              onDirtyChange={setDirty}
            />
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-5 lg:grid-cols-3">
          <Card className="lg:col-span-1">
            <CardHeader><CardTitle className="text-base">Basic information</CardTitle></CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">Mobile</span><span className="tabular">{customer.mobileNo}</span></div>
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">Email</span><span className="truncate">{customer.email ?? '—'}</span></div>
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">GSTIN</span><span className="tabular">{customer.gstNo ?? '—'}</span></div>
              <div>
                <p className="text-muted-foreground">Address</p>
                <p>{customer.addressLine1 ?? '—'}</p>
                {customer.addressLine2 ? <p>{customer.addressLine2}</p> : null}
                <p>{[customer.city, customer.pincode].filter(Boolean).join(' · ') || '—'}</p>
              </div>
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">Status</span><span>{customer.status === 'ACTIVE' ? 'Active' : 'Inactive'}</span></div>
              <div className="flex justify-between gap-3"><span className="text-muted-foreground">Credit limit</span><span className="tabular">₹{customer.creditLimitDisplay}</span></div>
            </CardContent>
          </Card>

          <div className="space-y-5 lg:col-span-2">
            <Card>
              <CardHeader><CardTitle className="text-base">Financial summary</CardTitle></CardHeader>
              <CardContent className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                <div>
                  <p className="text-xs text-muted-foreground">Total invoiced</p>
                  <p className="tabular text-lg font-semibold">₹{summary.totalInvoicedDisplay}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Total paid</p>
                  <p className="tabular text-lg font-semibold">₹{summary.totalPaidDisplay}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Outstanding balance</p>
                  <p className="tabular text-lg font-semibold text-warning">₹{summary.outstandingBalanceDisplay}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Invoices / Quotations</p>
                  <p className="tabular text-lg font-semibold">{summary.invoiceCount} / {summary.quotationCount}</p>
                </div>
              </CardContent>
            </Card>

            <Tabs defaultValue="invoices">
              <TabsList>
                <TabsTrigger value="invoices">Invoices</TabsTrigger>
                <TabsTrigger value="quotations">Quotations</TabsTrigger>
                <TabsTrigger value="products">Products purchased</TabsTrigger>
              </TabsList>

              <TabsContent value="invoices">
                <Card>
                  <CardContent className="space-y-1 pt-6">
                    {!invoices || invoices.content.length === 0 ? (
                      <EmptyState icon={FileText} title="No invoices yet" description="Invoices raised for this customer appear here." />
                    ) : (
                      invoices.content.map((invoice) => (
                        <Link
                          key={invoice.id}
                          to={INVOICE_ROUTES.detail(invoice.id)}
                          className="flex items-center justify-between rounded-md px-2 py-2 text-sm hover:bg-accent"
                        >
                          <span>
                            <span className="block font-medium">{invoice.invoiceNumber}</span>
                            <span className="block text-xs text-muted-foreground">{invoice.invoiceDate}</span>
                          </span>
                          <span className="flex items-center gap-3">
                            <IndianRupee className="h-3.5 w-3.5 text-muted-foreground" aria-hidden />
                            <span className="tabular">{invoice.totalDisplay}</span>
                            <InvoiceStatusBadge status={invoice.status} />
                          </span>
                        </Link>
                      ))
                    )}
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="quotations">
                <Card>
                  <CardContent className="space-y-1 pt-6">
                    {!quotations || quotations.content.length === 0 ? (
                      <EmptyState icon={ClipboardList} title="No quotations yet" description="Price quotes given to this customer appear here." />
                    ) : (
                      quotations.content.map((quotation) => (
                        <Link
                          key={quotation.id}
                          to={QUOTATION_ROUTES.detail(quotation.id)}
                          className="flex items-center justify-between rounded-md px-2 py-2 text-sm hover:bg-accent"
                        >
                          <span>
                            <span className="block font-medium">{quotation.quotationNumber}</span>
                            <span className="block text-xs text-muted-foreground">{quotation.quotationDate}</span>
                          </span>
                          <span className="flex items-center gap-3">
                            <IndianRupee className="h-3.5 w-3.5 text-muted-foreground" aria-hidden />
                            <span className="tabular">{quotation.totalDisplay}</span>
                            <QuotationStatusBadge status={quotation.status} />
                          </span>
                        </Link>
                      ))
                    )}
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="products">
                <Card>
                  {products.length === 0 ? (
                    <EmptyState icon={Plus} title="No purchases yet" description="Products this customer has bought before appear here, with the price they last paid." />
                  ) : (
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Product</TableHead>
                          <TableHead className="text-right">Total qty</TableHead>
                          <TableHead className="text-right">Last price</TableHead>
                          <TableHead>Last purchased</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {products.map((p) => (
                          <TableRow key={p.productId}>
                            <TableCell><span className="font-medium">{p.productName}</span><span className="block text-xs text-muted-foreground">{p.productCode}</span></TableCell>
                            <TableCell className="tabular text-right">{p.totalQuantityPurchased} {p.unit}</TableCell>
                            <TableCell className="tabular text-right">₹{p.lastPriceDisplay}</TableCell>
                            <TableCell>{p.lastPurchaseDate}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  )}
                </Card>
              </TabsContent>
            </Tabs>
          </div>
        </div>
      )}

      <UnsavedChangesDialog
        open={confirmingClose}
        onContinueEditing={() => setConfirmingClose(false)}
        onDiscard={() => { setConfirmingClose(false); setDirty(false); setEditMode(false); }}
        formId={CUSTOMER_FORM_ID}
      />
    </>
  );
}
