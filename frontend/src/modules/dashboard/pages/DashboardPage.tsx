import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle, ClipboardList, FileText, IndianRupee, Package, Plus, TrendingUp, Truck, Users, Wallet,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import { EmptyState } from '@/shared/components/EmptyState';
import { PermissionGate } from '@/routes/RequirePermission';
import { SalesTrendChart } from '../components/SalesTrendChart';
import { SalesByCategoryChart } from '../components/SalesByCategoryChart';
import { CountCard, KpiCard } from '../components/StatCards';
import { QuickActionsCard } from '../components/QuickActionsCard';
import { RecentActionsCard } from '../components/RecentActionsCard';
import { ShopTimeChip, greetingFor } from '../components/ShopTimeChip';
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
import { analyticsService, type TrendPoint } from '../services/analyticsService';
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

/**
 * CR-082. The approved dashboard, built to the signed-off mockup.
 *
 * Widget titles are counter-staff language rather than the module names in
 * the rail - "Bills Raised", not "Invoices" - so a card and a menu entry are
 * never the same words pointing at two different things.
 *
 * What is measured and what is not, because the mockup drew sparklines on
 * every card: Total Sales and Today's Earnings have a real daily revenue
 * series behind them (/v1/analytics/revenue-trend, CR-048) and their
 * sparklines and week-over-week deltas are computed from it. Pending
 * Payments and Low Stock Alerts have no time series in the API, so those
 * cards carry no sparkline and no delta. A line drawn from nothing would
 * be decoration pretending to be data.
 */
interface Count {
  id: 'products' | 'suppliers' | 'invoices' | 'customers' | 'low-stock';
  label: string;
  value: number | null;
  icon: typeof Package;
  to: string;
  tone?: 'primary' | 'warning';
}

/** Fourteen days of daily revenue: the last 7 for the sparkline and delta, the 7 before for the comparison. */
const LOOKBACK_DAYS = 14;

const iso = (d: Date) => d.toISOString().slice(0, 10);
const sum = (points: TrendPoint[]) => points.reduce((total, p) => total + p.revenuePaise, 0);
const percentChange = (now: number, before: number): number | null => (before > 0 ? ((now - before) / before) * 100 : null);

export function DashboardPage() {
  const { user, hasPermission } = useAuth();
  const { motion } = useDesignStyle();
  const entranceClass = motion !== 'reduced' ? 'animate-in fade-in slide-in-from-bottom-2 duration-500' : '';
  const [counts, setCounts] = useState<Count[]>([]);
  const [sales, setSales] = useState<SalesSummaryResponse | null>(null);
  const [trend, setTrend] = useState<TrendPoint[] | null>(null);
  const [recentInvoices, setRecentInvoices] = useState<InvoiceSummaryResponse[] | null>(null);
  const [pendingQuotations, setPendingQuotations] = useState<QuotationSummaryResponse[] | null>(null);
  const [recentCustomers, setRecentCustomers] = useState<CustomerSummaryResponse[] | null>(null);
  const [lowStock, setLowStock] = useState<StockResponse[] | null>(null);

  useEffect(() => {
    const loaders: Array<Promise<Count>> = [];
    if (hasPermission(PERMISSIONS.PRODUCT_VIEW)) {
      loaders.push(productService.search({ size: 1 })
        .then((page) => ({ id: 'products' as const, label: 'Items Catalog', value: page.totalElements, icon: Package, to: PRODUCT_ROUTES.list }))
        .catch(() => ({ id: 'products' as const, label: 'Items Catalog', value: null, icon: Package, to: PRODUCT_ROUTES.list })));
    }
    if (hasPermission(PERMISSIONS.SUPPLIER_VIEW)) {
      loaders.push(supplierService.search({ size: 1 })
        .then((page) => ({ id: 'suppliers' as const, label: 'Wholesalers & Dealers', value: page.totalElements, icon: Truck, to: SUPPLIER_ROUTES.list }))
        .catch(() => ({ id: 'suppliers' as const, label: 'Wholesalers & Dealers', value: null, icon: Truck, to: SUPPLIER_ROUTES.list })));
    }
    if (hasPermission(PERMISSIONS.INVENTORY_VIEW)) {
      loaders.push(stockService.search({ lowStockOnly: true, size: 1 })
        .then((page) => ({ id: 'low-stock' as const, label: 'Low Stock Alerts', value: page.totalElements, icon: AlertTriangle, to: INVENTORY_ROUTES.stock, tone: 'warning' as const }))
        .catch(() => ({ id: 'low-stock' as const, label: 'Low Stock Alerts', value: null, icon: AlertTriangle, to: INVENTORY_ROUTES.stock, tone: 'warning' as const })));
    }
    if (hasPermission(PERMISSIONS.INVOICE_VIEW)) {
      loaders.push(invoiceService.search({ size: 1 })
        .then((page) => ({ id: 'invoices' as const, label: 'Bills Raised', value: page.totalElements, icon: FileText, to: INVOICE_ROUTES.list }))
        .catch(() => ({ id: 'invoices' as const, label: 'Bills Raised', value: null, icon: FileText, to: INVOICE_ROUTES.list })));
    }
    if (hasPermission(PERMISSIONS.CUSTOMER_VIEW)) {
      loaders.push(customerService.search({ size: 1 })
        .then((page) => ({ id: 'customers' as const, label: 'Customer List', value: page.totalElements, icon: Users, to: CUSTOMER_ROUTES.list }))
        .catch(() => ({ id: 'customers' as const, label: 'Customer List', value: null, icon: Users, to: CUSTOMER_ROUTES.list })));
    }
    void Promise.all(loaders).then(setCounts);

    if (hasPermission(PERMISSIONS.INVOICE_VIEW)) {
      invoiceService.search({ size: 5 }).then((page) => setRecentInvoices(page.content)).catch(() => setRecentInvoices([]));
      dashboardService.salesSummary().then(setSales).catch(() => setSales(null));
    }
    // The same endpoint the Sales Growth chart reads, gated the same way.
    if (hasPermission(PERMISSIONS.REPORT_VIEW)) {
      const to = new Date();
      const from = new Date();
      from.setDate(to.getDate() - (LOOKBACK_DAYS - 1));
      analyticsService.revenueTrend(iso(from), iso(to), 'day')
        .then((series) => setTrend(series?.points ?? []))
        .catch(() => setTrend(null));
    }
    if (hasPermission(PERMISSIONS.QUOTATION_VIEW)) {
      // "Pending Estimates" means it: quotations sent and not yet answered,
      // not the last five of any status.
      quotationService.search({ status: 'SENT', size: 5 }).then((page) => setPendingQuotations(page.content)).catch(() => setPendingQuotations([]));
    }
    if (hasPermission(PERMISSIONS.CUSTOMER_VIEW)) {
      customerService.search({ size: 5 }).then((page) => setRecentCustomers(page.content)).catch(() => setRecentCustomers([]));
    }
    if (hasPermission(PERMISSIONS.INVENTORY_VIEW)) {
      stockService.search({ lowStockOnly: true, size: 5 }).then((page) => setLowStock(page.content)).catch(() => setLowStock([]));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /*
   * Week-over-week from the 14-day series. Buckets arrive oldest first; a
   * shop younger than 14 days simply has a shorter "before" window, and the
   * delta is null (shown as "—") when that window sums to zero.
   */
  const weekly = useMemo(() => {
    if (!trend || trend.length === 0) return null;
    const thisWeek = trend.slice(-7);
    const lastWeek = trend.slice(-14, -7);
    return {
      series: trend.map((p) => p.revenuePaise),
      thisWeekSeries: thisWeek.map((p) => p.revenuePaise),
      change: percentChange(sum(thisWeek), sum(lastWeek)),
    };
  }, [trend]);

  const todayChange = sales
    ? percentChange(sales.todaySalesPaise, sales.yesterdaySalesPaise)
    : null;

  const lowStockCount = counts.find((c) => c.id === 'low-stock');
  const secondRow = counts.filter((c) => c.id !== 'low-stock');
  const firstName = user?.fullName?.split(' ')[0];

  return (
    <>
      {/* Page header: greeting eyebrow, title, subtitle; date chip and the
          two primary actions on the right. */}
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <p className="text-xs font-bold uppercase tracking-[0.1em] text-primary">{greetingFor(new Date().getHours())},</p>
          <h1 className="mt-1 truncate text-2xl font-bold tracking-tight sm:text-[28px]">
            Welcome back{firstName ? `, ${firstName}` : ''}
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">Here&apos;s what&apos;s happening with your shop today.</p>
        </div>
        <div className="flex flex-wrap items-center gap-3 lg:justify-end">
          <ShopTimeChip />
          <span className="hidden h-8 w-px bg-border sm:block" aria-hidden />
          <div className="flex flex-wrap items-center gap-2">
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
        </div>
      </div>

      {/* Row 1: the money and the one thing that needs attention. */}
      {sales || lowStockCount ? (
        <div className={`grid gap-4 sm:grid-cols-2 xl:grid-cols-4 ${entranceClass}`}>
          {sales ? (
            <>
              <KpiCard
                label="Total Sales"
                value={`₹${sales.totalSalesDisplay}`}
                icon={TrendingUp}
                delta={weekly ? { percent: weekly.change, against: 'vs last week', upIsGood: true } : undefined}
                series={weekly?.series}
              />
              <KpiCard
                label="Today's Earnings"
                value={`₹${sales.todaySalesDisplay}`}
                icon={IndianRupee}
                delta={{ percent: todayChange, against: 'vs yesterday', upIsGood: true }}
                series={weekly?.thisWeekSeries}
              />
              <KpiCard
                label="Pending Payments"
                value={`₹${sales.outstandingCustomerBalanceDisplay}`}
                icon={Wallet}
                to={INVOICE_ROUTES.list}
              />
            </>
          ) : null}
          {lowStockCount ? (
            <KpiCard
              label={lowStockCount.label}
              value={String(lowStockCount.value ?? '—')}
              icon={AlertTriangle}
              tone="warning"
              to={lowStockCount.to}
            />
          ) : null}
        </div>
      ) : null}

      {/* Row 2: counts, each the door to its list. */}
      {secondRow.length > 0 ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {secondRow.map((c) => (
            <CountCard key={c.id} label={c.label} value={c.value} icon={c.icon} to={c.to} tone={c.tone} />
          ))}
        </div>
      ) : null}

      {/* Charts, 2:1. REPORT_VIEW gates both - the analytics endpoints need it,
          so the cards are never shown to someone whose request would 403. */}
      <PermissionGate permission={PERMISSIONS.REPORT_VIEW}>
        <div className="grid gap-5 lg:grid-cols-3">
          <div className="lg:col-span-2"><SalesTrendChart /></div>
          <SalesByCategoryChart />
        </div>
      </PermissionGate>

      {/* Quick actions and Recent actions, same 2:1. The activity log needs
          AUDIT_VIEW; without it the actions card takes the full width. */}
      <div className="grid gap-5 lg:grid-cols-3">
        <div className="lg:col-span-2"><QuickActionsCard /></div>
        <PermissionGate permission={PERMISSIONS.AUDIT_VIEW}>
          <RecentActionsCard />
        </PermissionGate>
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <PermissionGate permission={PERMISSIONS.INVOICE_VIEW}>
          <Card>
            <CardHeader><CardTitle className="text-base">Recent Bills</CardTitle></CardHeader>
            <CardContent className="space-y-1">
              {recentInvoices === null ? (
                <p className="py-6 text-center text-sm text-muted-foreground">Loading…</p>
              ) : recentInvoices.length === 0 ? (
                <EmptyState icon={FileText} title="No bills yet" description="Raise the first one to see it here." />
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
            <CardHeader><CardTitle className="text-base">Low Stock Alerts</CardTitle></CardHeader>
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
            <CardHeader><CardTitle className="text-base">Pending Estimates</CardTitle></CardHeader>
            <CardContent className="space-y-1">
              {pendingQuotations === null ? (
                <p className="py-6 text-center text-sm text-muted-foreground">Loading…</p>
              ) : pendingQuotations.length === 0 ? (
                <EmptyState icon={ClipboardList} title="No estimates waiting" description="Quotations you have sent and not yet heard back on appear here." />
              ) : (
                pendingQuotations.map((quotation) => (
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
            <CardHeader><CardTitle className="text-base">Recent Customers</CardTitle></CardHeader>
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
