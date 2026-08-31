import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ClipboardList, Command, FileText, Package, Search, Truck, Users,
} from 'lucide-react';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { navigableItems } from '@/layouts/Sidebar';
import { productService } from '@/modules/product/services/productService';
import { supplierService } from '@/modules/supplier/services/supplierService';
import { customerService } from '@/modules/customer/services/customerService';
import { invoiceService } from '@/modules/invoice/services/invoiceService';
import { quotationService } from '@/modules/quotation/services/quotationService';
import { PRODUCT_ROUTES } from '@/modules/product/constants';
import { SUPPLIER_ROUTES } from '@/modules/supplier/constants';
import { CUSTOMER_ROUTES } from '@/modules/customer/constants';
import { INVOICE_ROUTES } from '@/modules/invoice/constants';
import { QUOTATION_ROUTES } from '@/modules/quotation/constants';
import type { ProductSummaryResponse } from '@/modules/product/types';
import type { SupplierSummaryResponse } from '@/modules/supplier/types';
import type { CustomerSummaryResponse } from '@/modules/customer/types';
import type { InvoiceSummaryResponse } from '@/modules/invoice/types';
import type { QuotationSummaryResponse } from '@/modules/quotation/types';

/**
 * Searches across every module that actually has data today (Products,
 * Suppliers, Customers, Invoices, Quotations) plus page navigation
 * (CR-033 - Sidebar.navigableItems() was built for exactly this and sat
 * unused). The box used to render disabled with a "coming soon" tooltip -
 * once these modules shipped that became actively confusing rather than
 * honest, so it is wired for real now. A search failing for one module
 * (e.g. 403 for a caller without CUSTOMER_VIEW) just renders that section
 * empty via Promise.allSettled, rather than failing the whole search.
 */
export function GlobalSearch() {
  const navigate = useNavigate();
  const { hasPermission } = useAuth();
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [products, setProducts] = useState<ProductSummaryResponse[]>([]);
  const [suppliers, setSuppliers] = useState<SupplierSummaryResponse[]>([]);
  const [customers, setCustomers] = useState<CustomerSummaryResponse[]>([]);
  const [invoices, setInvoices] = useState<InvoiceSummaryResponse[]>([]);
  const [quotations, setQuotations] = useState<QuotationSummaryResponse[]>([]);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const pages = useMemo(() => navigableItems(hasPermission), [hasPermission]);
  const matchingPages = useMemo(() => {
    const term = query.trim().toLowerCase();
    if (term.length < 1) return [];
    return pages.filter((page) => page.label.toLowerCase().includes(term)).slice(0, 5);
  }, [pages, query]);

  useEffect(() => {
    const term = query.trim();
    if (term.length < 1) {
      setProducts([]);
      setSuppliers([]);
      setCustomers([]);
      setInvoices([]);
      setQuotations([]);
      return;
    }
    setLoading(true);
    const timer = setTimeout(() => {
      Promise.allSettled([
        productService.search({ search: term, size: 5 }),
        supplierService.search({ search: term, size: 5 }),
        customerService.search({ search: term, size: 5 }),
        invoiceService.search({ search: term, size: 5 }),
        quotationService.search({ search: term, size: 5 }),
      ]).then(([productResult, supplierResult, customerResult, invoiceResult, quotationResult]) => {
        setProducts(productResult.status === 'fulfilled' ? productResult.value.content : []);
        setSuppliers(supplierResult.status === 'fulfilled' ? supplierResult.value.content : []);
        setCustomers(customerResult.status === 'fulfilled' ? customerResult.value.content : []);
        setInvoices(invoiceResult.status === 'fulfilled' ? invoiceResult.value.content : []);
        setQuotations(quotationResult.status === 'fulfilled' ? quotationResult.value.content : []);
      }).finally(() => setLoading(false));
    }, 250);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const handleShortcut = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
        event.preventDefault();
        inputRef.current?.focus();
        setOpen(true);
      }
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleShortcut);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleShortcut);
    };
  }, []);

  const goTo = (path: string) => {
    navigate(path);
    setQuery('');
    setOpen(false);
  };

  const hasResults = products.length > 0 || suppliers.length > 0 || customers.length > 0
    || invoices.length > 0 || quotations.length > 0 || matchingPages.length > 0;

  return (
    <div ref={containerRef} className="relative hidden flex-1 max-w-md sm:block">
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
        <input
          ref={inputRef}
          value={query}
          onChange={(event) => { setQuery(event.target.value); setOpen(true); }}
          onFocus={() => setOpen(true)}
          placeholder="Search anything or jump to a page…"
          className="h-9 w-full rounded-md border border-input bg-background pl-9 pr-14 text-sm outline-none ring-offset-background placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring"
          aria-label="Search products, suppliers, customers, invoices, quotations and pages"
        />
        <kbd className="pointer-events-none absolute right-2 top-1/2 hidden -translate-y-1/2 items-center gap-0.5 rounded border px-1.5 py-0.5 text-[10px] text-muted-foreground md:inline-flex">
          <Command className="h-3 w-3" aria-hidden />K
        </kbd>
      </div>

      {open && query.trim().length > 0 ? (
        <div className="surface-overlay absolute z-30 mt-1 w-full overflow-hidden rounded-md border">
          <div className="max-h-96 overflow-y-auto py-1">
            {loading ? (
              <p className="px-3 py-3 text-sm text-muted-foreground">Searching…</p>
            ) : !hasResults ? (
              <p className="px-3 py-3 text-sm text-muted-foreground">No matches for &ldquo;{query}&rdquo;.</p>
            ) : (
              <>
                {matchingPages.length > 0 ? (
                  <div>
                    <p className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">Pages</p>
                    {matchingPages.map((page) => (
                      <button
                        key={page.to}
                        type="button"
                        onClick={() => goTo(page.to)}
                        className="flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm hover:bg-accent"
                      >
                        <page.icon className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                        <span className="min-w-0">
                          <span className="block truncate font-medium">{page.label}</span>
                          <span className="block text-xs text-muted-foreground">{page.section}</span>
                        </span>
                      </button>
                    ))}
                  </div>
                ) : null}
                {invoices.length > 0 ? (
                  <div>
                    <p className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">Invoices</p>
                    {invoices.map((invoice) => (
                      <button
                        key={invoice.id}
                        type="button"
                        onClick={() => goTo(INVOICE_ROUTES.detail(invoice.id))}
                        className="flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm hover:bg-accent"
                      >
                        <FileText className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                        <span className="min-w-0">
                          <span className="block truncate font-medium">{invoice.invoiceNumber}</span>
                          <span className="block text-xs text-muted-foreground">{invoice.customerName} · ₹{invoice.totalDisplay}</span>
                        </span>
                      </button>
                    ))}
                  </div>
                ) : null}
                {quotations.length > 0 ? (
                  <div>
                    <p className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">Quotations</p>
                    {quotations.map((quotation) => (
                      <button
                        key={quotation.id}
                        type="button"
                        onClick={() => goTo(QUOTATION_ROUTES.detail(quotation.id))}
                        className="flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm hover:bg-accent"
                      >
                        <ClipboardList className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                        <span className="min-w-0">
                          <span className="block truncate font-medium">{quotation.quotationNumber}</span>
                          <span className="block text-xs text-muted-foreground">{quotation.customerName}</span>
                        </span>
                      </button>
                    ))}
                  </div>
                ) : null}
                {products.length > 0 ? (
                  <div>
                    <p className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">Products</p>
                    {products.map((product) => (
                      <button
                        key={product.id}
                        type="button"
                        onClick={() => goTo(PRODUCT_ROUTES.detail(product.id))}
                        className="flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm hover:bg-accent"
                      >
                        <Package className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                        <span className="min-w-0">
                          <span className="block truncate font-medium">{product.productName}</span>
                          <span className="block text-xs text-muted-foreground">{product.productCode}</span>
                        </span>
                      </button>
                    ))}
                  </div>
                ) : null}
                {suppliers.length > 0 ? (
                  <div>
                    <p className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">Suppliers</p>
                    {suppliers.map((supplier) => (
                      <button
                        key={supplier.id}
                        type="button"
                        onClick={() => goTo(SUPPLIER_ROUTES.detail(supplier.id))}
                        className="flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm hover:bg-accent"
                      >
                        <Truck className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                        <span className="min-w-0">
                          <span className="block truncate font-medium">{supplier.supplierName}</span>
                          <span className="block text-xs text-muted-foreground">{supplier.supplierCode}</span>
                        </span>
                      </button>
                    ))}
                  </div>
                ) : null}
                {customers.length > 0 ? (
                  <div>
                    <p className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">Customers</p>
                    {customers.map((customer) => (
                      <button
                        key={customer.id}
                        type="button"
                        onClick={() => goTo(CUSTOMER_ROUTES.detail(customer.id))}
                        className="flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm hover:bg-accent"
                      >
                        <Users className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                        <span className="min-w-0">
                          <span className="block truncate font-medium">{customer.customerName}</span>
                          <span className="block text-xs text-muted-foreground">{customer.customerCode} · {customer.mobileNo}</span>
                        </span>
                      </button>
                    ))}
                  </div>
                ) : null}
              </>
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}
