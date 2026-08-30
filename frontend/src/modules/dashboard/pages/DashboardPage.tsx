import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle, ArrowDown, ArrowUp, ClipboardList, FileText, IndianRupee, Package, Plus, TrendingUp, Truck, Users,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { PermissionGate } from '@/routes/RequirePermission';
import { SalesTrendChart } from '../components/SalesTrendChart';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { useDesignStyle } from '@/theme/DesignStyleProvider';
import { productService } from '@/modules/product/services/productService';
import { supplierService } from '@/modules/supplier/services/supplierService';
import { stockService } from '@/modules/inventory/services/stockService';
import { invoiceService } from '@/modules/invoice/services/invoiceService';
import { quotationService } from '@/modules/quotation/services/quotationService';
import { customerService } from '@/modules/customer/services/customerService';
import { dashboardService, type SalesSummaryResponse } from '../services/dashboardService';
import { INVOICE_ROUTES } from '@/modules/invoice/constants';
import { QUOTATION_ROUTES } from '@/modules/quotation/constants';
import { CUSTOMER_ROUTES } from '@/modules/customer/constants';
import { INVENTORY_ROUTES } from '@/modules/inventory/constants';
import { PRODUCT_ROUTES } from '@/modules/product/constants';
import { SUPPLIER_ROUTES } from '@/modules/supplier/constants';
import { InvoiceStatusBadge } from '@/modules/invoice/components/InvoiceStatusBadge';
import { QuotationStatusBadge } from '@/modules/quotation/components/QuotationStatusBadge';
import type { InvoiceSummaryResponse } from '@/modules/invoice/types';
import type { QuotationSummaryResponse } from '@/modules/quotation/types';
import type { CustomerSummaryResponse } from '@/modules/customer/types';
import type { StockResponse } from '@/modules/inventory/types';

interface Stat {
  label: string;
  value: number | null;
  icon: typeof Package;
  to: string;
  tone?: 'default' | 'warning';
}

export function DashboardPage() {
  const { user, hasPermission } = useAuth();
  const { designStyleId, motion } = useDesignStyle();
  const isBento = designStyleId === 'bento';
  const entranceClass = motion !== 'reduced' ? 'animate-in fade-in slide-in-from-bottom-2 duration-500' : '';
  const [stats, setStats] = useState<Stat[]>([]);
  const [sales, setSales] = useState<SalesSummaryResponse | null>(null);
  const [recentInvoices, setRecentInvoices] = useState<InvoiceSummaryResponse[] | null>(null);
  const [recentQuotations, setRecentQuotations] = useState<QuotationSummaryResponse[] | null>(null);
  const [recentCustomers, setRecentCustomers] = useState<CustomerSummaryResponse[] | null>(null);
  const [lowStock, setLowStock] = useState<StockResponse[] | null>(null);

  useEffect(() => {
    const loaders: Array<Promise<Stat>> = [];

    if (hasPermission(PERMISSIONS.PRODUCT_VIEW)) {
      loaders.push(productService.search({ size: 1 })
        .then((page) => ({ label: 'Products', value: page.totalElements, icon: Package, to: PRODUCT_ROUTES.list }))
        .catch(() => ({ label: 'Products', value: null, icon: Package, to: PRODUCT_ROUTES.list })));
    }
    if (hasPermission(PERMISSIONS.SUPPLIER_VIEW)) {
      loaders.push(supplierService.search({ size: 1 })
        .then((page) => ({ label: 'Suppliers', value: page.totalElements, icon: Truck, to: SUPPLIER_ROUTES.list }))
        .catch(() => ({ label: 'Suppliers', value: null, icon: Truck, to: SUPPLIER_ROUTES.list })));
    }
    if (hasPermission(PERMISSIONS.INVENTORY_VIEW)) {
      loaders.push(stockService.search({ lowStockOnly: true, size: 1 })
        .then((page) => ({ label: 'Low stock items', value: page.totalElements, icon: AlertTriangle, to: INVENTORY_ROUTES.stock, tone: 'warning' as const }))
        .catch(() => ({ label: 'Low stock items', value: null, icon: AlertTriangle, to: INVENTORY_ROUTES.stock, tone: 'warning' as const })));
    }
    if (hasPermission(PERMISSIONS.INVOICE_VIEW)) {
      loaders.push(invoiceService.search({ size: 1 })
        .then((page) => ({ label: 'Invoices', value: page.totalElements, icon: FileText, to: INVOICE_ROUTES.list }))
        .catch(() => ({ label: 'Invoices', value: null, icon: FileText, to: INVOICE_ROUTES.list })));
    }
    if (hasPermission(PERMISSIONS.CUSTOMER_VIEW)) {
      loaders.push(customerService.search({ size: 1 })
        .then((page) => ({ label: 'Customers', value: page.totalElements, icon: Users, to: CUSTOMER_ROUTES.list }))
        .catch(() => ({ label: 'Customers', value: null, icon: Users, to: CUSTOMER_ROUTES.list })));
    }

    void Promise.all(loaders).then(setStats);

    if (hasPermission(PERMISSIONS.INVOICE_VIEW)) {
      invoiceService.search({ size: 5 }).then((page) => setRecentInvoices(page.content)).catch(() => setRecentInvoices([]));
      dashboardService.salesSummary().then(setSales).catch(() => setSales(null));
    }
    if (hasPermission(PERMISSIONS.QUOTATION_VIEW)) {
      quotationService.search({ size: 5 }).then((page) => setRecentQuotations(page.content)).catch(() => setRecentQuotations([]));
    }
    if (hasPermission(PERMISSIONS.CUSTOMER_VIEW)) {
      customerService.search({ size: 5 }).then((page) => setRecentCustomers(page.content)).catch(() => setRecentCustomers([]));
    }
    if (hasPermission(PERMISSIONS.INVENTORY_VIEW)) {
      stockService.search({ lowStockOnly: true, size: 5 }).then((page) => setLowStock(page.content)).catch(() => setLowStock([]));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <>
      <PageHeader
        title={`Welcome back${user?.fullName ? `, ${user.fullName.split(' ')[0]}` : ''}`}
        description="A quick look at the shop today."
        actions={
          <div className="flex items-center gap-2">
            <PermissionGate permission={PERMISSIONS.QUOTATION_MANAGE}>
              <Button variant="outline" asChild>
                <Link to={QUOTATION_ROUTES.create}><Plus className="h-4 w-4" />New quotation</Link>
              </Button>
            </PermissionGate>
            <PermissionGate permission={PERMISSIONS.INVOICE_CREATE}>
              <Button asChild>
                <Link to={INVOICE_ROUTES.create}><Plus className="h-4 w-4" />New invoice</Link>
              </Button>
            </PermissionGate>
          </div>
        }
      />

      {sales ? (() => {
        const totalCard = (
          <Card key="total" className={entranceClass}>
            <CardContent className="flex h-full items-center justify-between p-5">
              <div>
                <p className="text-sm text-muted-foreground">Total sales</p>
                <p className="figure mt-1 text-2xl">₹{sales.totalSalesDisplay}</p>
              </div>
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-primary">
                <TrendingUp className="h-5 w-5" aria-hidden />
              </span>
            </CardContent>
          </Card>
        );
        const todayChange = sales.yesterdaySalesPaise > 0
          ? ((sales.todaySalesPaise - sales.yesterdaySalesPaise) / sales.yesterdaySalesPaise) * 100
          : null;
        const todayCard = (
          <Card key="today" className={entranceClass}>
            <CardContent className={isBento ? 'flex h-full flex-col justify-center gap-2 p-6' : 'flex h-full items-center justify-between p-5'}>
              <div className="flex items-center justify-between">
                <p className={isBento ? 'text-sm text-muted-foreground' : 'text-sm text-muted-foreground'}>Today&apos;s sales</p>
                <span className="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-primary">
                  <IndianRupee className="h-5 w-5" aria-hidden />
                </span>
              </div>
              <div>
                <p className={isBento ? 'figure text-4xl' : 'figure mt-1 text-2xl'}>₹{sales.todaySalesDisplay}</p>
                {todayChange !== null ? (
                  <p className={`mt-1 flex items-center gap-1 text-xs ${todayChange >= 0 ? 'text-success' : 'text-destructive'}`}>
                    {todayChange >= 0 ? <ArrowUp className="h-3 w-3" aria-hidden /> : <ArrowDown className="h-3 w-3" aria-hidden />}
                    {Math.abs(todayChange).toFixed(1)}% vs yesterday
                  </p>
                ) : null}
              </div>
            </CardContent>
          </Card>
        );
        const outstandingCard = (
          <Card key="outstanding" className={entranceClass}>
            <CardContent className="flex h-full items-center justify-between p-5">
              <div>
                <p className="text-sm text-muted-foreground">Outstanding customer balance</p>
                <p className="figure mt-1 text-2xl">₹{sales.outstandingCustomerBalanceDisplay}</p>
              </div>
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-warning/10 text-warning">
                <AlertTriangle className="h-5 w-5" aria-hidden />
              </span>
            </CardContent>
          </Card>
        );

        // CR-034 §21: Bento leads with the day's figure as the large "hero" cell (.bento-grid in index.css); every other
        // design style keeps the original even 3-up grid. Same data and cards either way - only the layout changes.
        return isBento
          ? <div className="bento-grid">{[todayCard, totalCard, outstandingCard]}</div>
          : <div className="grid gap-4 sm:grid-cols-3">{[totalCard, todayCard, outstandingCard]}</div>;
      })() : null}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => (
          <Link key={stat.label} to={stat.to}>
            <Card className="transition-colors hover:bg-accent/40">
              <CardContent className="flex items-center justify-between p-5">
                <div>
                  <p className="text-sm text-muted-foreground">{stat.label}</p>
                  <p className="figure mt-1 text-2xl">{stat.value ?? '—'}</p>
                </div>
                <span
                  className={`flex h-10 w-10 items-center justify-center rounded-full ${
                    stat.tone === 'warning' ? 'bg-warning/10 text-warning' : 'bg-primary/10 text-primary'
                  }`}
                >
                  <stat.icon className="h-5 w-5" aria-hidden />
                </span>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>

      {/* Full width: a trend needs horizontal room to be readable, and it is
          the one thing on this page that answers 'how is the shop doing'
          rather than 'what is the number right now'. REPORT_VIEW gates it -
          the same permission the analytics endpoints require, so the card is
          never shown to someone whose request would 403. */}
      <PermissionGate permission={PERMISSIONS.REPORT_VIEW}>
        <SalesTrendChart />
      </PermissionGate>

      <div className="grid gap-5 lg:grid-cols-2">
        <PermissionGate permission={PERMISSIONS.INVOICE_VIEW}>
          <Card>
            <CardHeader><CardTitle className="text-base">Recent invoices</CardTitle></CardHeader>
            <CardContent className="space-y-1">
              {recentInvoices === null ? (
                <p className="py-6 text-center text-sm text-muted-foreground">Loading…</p>
              ) : recentInvoices.length === 0 ? (
                <EmptyState icon={FileText} title="No invoices yet" description="Create the first one to see it here." />
              ) : (
                recentInvoices.map((invoice) => (
                  <Link
                    key={invoice.id}
                    to={INVOICE_ROUTES.detail(invoice.id)}
                    className="flex items-center justify-between rounded-md px-2 py-2 text-sm hover:bg-accent"
                  >
                    <span>
                      <span className="block font-medium">{invoice.invoiceNumber}</span>
                      <span className="block text-xs text-muted-foreground">{invoice.customerName}</span>
                    </span>
                    <span className="flex items-center gap-3">
                      <span className="tabular">₹{invoice.totalDisplay}</span>
                      <InvoiceStatusBadge status={invoice.status} />
                    </span>
                  </Link>
                ))
              )}
            </CardContent>
          </Card>
        </PermissionGate>

        <PermissionGate permission={PERMISSIONS.INVENTORY_VIEW}>
          <Card>
            <CardHeader><CardTitle className="text-base">Low stock</CardTitle></CardHeader>
            <CardContent className="space-y-1">
              {lowStock === null ? (
                <p className="py-6 text-center text-sm text-muted-foreground">Loading…</p>
              ) : lowStock.length === 0 ? (
                <EmptyState icon={Package} title="Nothing is low on stock" />
              ) : (
                lowStock.map((row) => (
                  <Link
                    key={row.productId}
                    to={INVENTORY_ROUTES.stock}
                    className="flex items-center justify-between rounded-md px-2 py-2 text-sm hover:bg-accent"
                  >
                    <span>
                      <span className="block font-medium">{row.productName}</span>
                      <span className="block text-xs text-muted-foreground">{row.productCode}</span>
                    </span>
                    <span className="tabular text-warning">{row.quantityOnHand} {row.unit}</span>
                  </Link>
                ))
              )}
            </CardContent>
          </Card>
        </PermissionGate>

        <PermissionGate permission={PERMISSIONS.QUOTATION_VIEW}>
          <Card>
            <CardHeader><CardTitle className="text-base">Recent quotations</CardTitle></CardHeader>
            <CardContent className="space-y-1">
              {recentQuotations === null ? (
                <p className="py-6 text-center text-sm text-muted-foreground">Loading…</p>
              ) : recentQuotations.length === 0 ? (
                <EmptyState icon={ClipboardList} title="No quotations yet" description="Create the first one to see it here." />
              ) : (
                recentQuotations.map((quotation) => (
                  <Link
                    key={quotation.id}
                    to={QUOTATION_ROUTES.detail(quotation.id)}
                    className="flex items-center justify-between rounded-md px-2 py-2 text-sm hover:bg-accent"
                  >
                    <span>
                      <span className="block font-medium">{quotation.quotationNumber}</span>
                      <span className="block text-xs text-muted-foreground">{quotation.customerName}</span>
                    </span>
                    <span className="flex items-center gap-3">
                      <span className="tabular">₹{quotation.totalDisplay}</span>
                      <QuotationStatusBadge status={quotation.status} expired={quotation.expired} />
                    </span>
                  </Link>
                ))
              )}
            </CardContent>
          </Card>
        </PermissionGate>

        <PermissionGate permission={PERMISSIONS.CUSTOMER_VIEW}>
          <Card>
            <CardHeader><CardTitle className="text-base">Recent customers</CardTitle></CardHeader>
            <CardContent className="space-y-1">
              {recentCustomers === null ? (
                <p className="py-6 text-center text-sm text-muted-foreground">Loading…</p>
              ) : recentCustomers.length === 0 ? (
                <EmptyState icon={Users} title="No customers yet" />
              ) : (
                recentCustomers.map((customer) => (
                  <Link
                    key={customer.id}
                    to={CUSTOMER_ROUTES.detail(customer.id)}
                    className="flex items-center justify-between rounded-md px-2 py-2 text-sm hover:bg-accent"
                  >
                    <span>
                      <span className="block font-medium">{customer.customerName}</span>
                      <span className="block text-xs text-muted-foreground">{customer.mobileNo}</span>
                    </span>
                    <span className="text-xs text-muted-foreground">{customer.city ?? '—'}</span>
                  </Link>
                ))
              )}
            </CardContent>
          </Card>
        </PermissionGate>
      </div>
    </>
  );
}
