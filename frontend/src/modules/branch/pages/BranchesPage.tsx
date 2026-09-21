import { useCallback, useEffect, useState } from 'react';
import { ArrowRightLeft, Building2, Loader2, Pencil, Plus, Search } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
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
import {
  Tabs, TabsContent, TabsList, TabsTrigger,
} from '@/shared/components/ui/tabs';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { FormField } from '@/shared/components/FormField';
import { PageHeader } from '@/shared/components/PageHeader';
import { ApiError } from '@/shared/types/api';
import { formatDateTime } from '@/shared/lib/utils';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { toast as sonner } from 'sonner';
import { useToast } from '@/modules/auth/hooks/useToast';
import { useFeatureGate } from '@/modules/subscription/hooks/useFeatureGate';
import { UpgradeDialog } from '@/modules/subscription/components/UpgradeDialog';
import { productService } from '@/modules/product/services/productService';
import type { ProductSummaryResponse } from '@/modules/product/types';
import { branchService } from '../services/branchService';
import type {
  BranchRequest, BranchResponse, BranchStockResponse, BranchSummaryResponse, StockTransferResponse,
} from '../types';

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

const EMPTY_FORM: BranchRequest = {
  branchCode: '', branchName: '', addressLine1: '', city: '', stateCode: '', pincode: '', phone: '', status: 'ACTIVE',
};

/**
 * CR-092. Branches, the per-branch stock breakdown and transfers. The
 * breakdown is a measured split of the shop's stock, maintained by the
 * same code that moves stock; the shop's own figure stays the authority
 * for "can we sell this". A negative branch figure means goods were sold
 * from a branch that never received them by purchase or transfer - shown
 * as such, so the owner records the transfer that actually happened.
 */
export function BranchesPage() {
  const toast = useToast();
  const gate = useFeatureGate();
  const [branches, setBranches] = useState<BranchResponse[]>([]);
  const [summary, setSummary] = useState<BranchSummaryResponse[]>([]);
  const [stock, setStock] = useState<BranchStockResponse[]>([]);
  const [transfers, setTransfers] = useState<StockTransferResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [from, setFrom] = useState(() => isoDaysAgo(29));
  const [to, setTo] = useState(() => isoDaysAgo(0));
  const [stockBranch, setStockBranch] = useState<string>('all');
  const [stockSearch, setStockSearch] = useState('');

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<BranchResponse | null>(null);
  const [form, setForm] = useState<BranchRequest>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const [transferOpen, setTransferOpen] = useState(false);
  const [transferFrom, setTransferFrom] = useState('');
  const [transferTo, setTransferTo] = useState('');
  const [transferNotes, setTransferNotes] = useState('');
  const [transferLines, setTransferLines] = useState<{ product: ProductSummaryResponse; quantity: string }[]>([]);
  const [productQuery, setProductQuery] = useState('');
  const [productHits, setProductHits] = useState<ProductSummaryResponse[]>([]);
  const [transferring, setTransferring] = useState(false);

  const multiBranch = branches.length > 1;

  const reloadBranches = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await branchService.list();
      setBranches(list);
      if (list.length > 1) {
        const [sum, tr] = await Promise.all([branchService.summary(from, to), branchService.transfers(0, 20)]);
        setSummary(sum);
        setTransfers(tr.content);
      } else {
        setSummary([]);
        setTransfers([]);
      }
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : new ApiError({ message: 'Could not load branches', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [from, to]);

  const reloadStock = useCallback(async () => {
    if (!multiBranch) return;
    try {
      setStock(await branchService.stock(stockBranch === 'all' ? null : Number(stockBranch), stockSearch));
    } catch {
      // Stable callback on purpose: useToast() is a fresh object per render and would re-arm the effect below.
      sonner.error('Could not load branch stock.');
    }
  }, [multiBranch, stockBranch, stockSearch]);

  useEffect(() => { void reloadBranches(); }, [reloadBranches]);
  useEffect(() => { void reloadStock(); }, [reloadStock]);

  useEffect(() => {
    if (!transferOpen || productQuery.trim().length < 2) { setProductHits([]); return; }
    const handle = setTimeout(() => {
      productService.search({ search: productQuery.trim(), size: 8 })
        .then((page) => setProductHits(page.content))
        .catch(() => setProductHits([]));
    }, 250);
    return () => clearTimeout(handle);
  }, [productQuery, transferOpen]);

  const openCreate = () => { setEditing(null); setForm(EMPTY_FORM); setFormOpen(true); };
  const openEdit = (branch: BranchResponse) => {
    setEditing(branch);
    setForm({
      branchCode: branch.branchCode, branchName: branch.branchName, addressLine1: branch.addressLine1 ?? '',
      city: branch.city ?? '', stateCode: branch.stateCode ?? '', pincode: branch.pincode ?? '', phone: branch.phone ?? '',
      status: branch.status,
    });
    setFormOpen(true);
  };

  const saveBranch = async () => {
    setSaving(true);
    try {
      const body: BranchRequest = { ...form, branchCode: form.branchCode.trim().toUpperCase(), branchName: form.branchName.trim() };
      const result = await gate.guard(() => editing ? branchService.update(editing.id, body) : branchService.create(body));
      if (result === undefined) return;
      toast.success(editing ? 'Branch updated.' : 'Branch created.');
      setFormOpen(false);
      await reloadBranches();
    } catch (caught) {
      toast.error(caught, 'Could not save the branch.');
    } finally {
      setSaving(false);
    }
  };

  const addLine = (product: ProductSummaryResponse) => {
    if (transferLines.some((l) => l.product.id === product.id)) return;
    setTransferLines((lines) => [...lines, { product, quantity: '1' }]);
    setProductQuery('');
    setProductHits([]);
  };

  const submitTransfer = async () => {
    const items = transferLines
      .map((l) => ({ productId: l.product.id, quantity: Number(l.quantity) }))
      .filter((i) => Number.isFinite(i.quantity) && i.quantity > 0);
    if (!transferFrom || !transferTo || transferFrom === transferTo || items.length === 0) return;
    setTransferring(true);
    try {
      const result = await gate.guard(() => branchService.transfer({
        fromBranchId: Number(transferFrom), toBranchId: Number(transferTo), items, notes: transferNotes.trim() || null,
      }));
      if (result === undefined) return;
      toast.success(`Transfer ${result.transferNumber} recorded.`);
      setTransferOpen(false);
      setTransferLines([]);
      setTransferNotes('');
      await Promise.all([reloadBranches(), reloadStock()]);
    } catch (caught) {
      toast.error(caught, 'Could not record the transfer.');
    } finally {
      setTransferring(false);
    }
  };

  if (loading && branches.length === 0) {
    return <div className="flex items-center justify-center py-16 text-muted-foreground"><Loader2 className="h-5 w-5 animate-spin" aria-label="Loading" /></div>;
  }
  if (error) return <ErrorState error={error} onRetry={() => void reloadBranches()} />;

  return (
    <>
      <PageHeader
        title="Branches"
        description="Where your stock and sales are. Every shop starts with its main branch; add more to track each location separately."
        actions={(
          <div className="flex gap-2">
            {multiBranch ? (
              <PermissionGate permission={PERMISSIONS.STOCK_TRANSFER_MANAGE}>
                <Button type="button" variant="outline" onClick={() => setTransferOpen(true)}>
                  <ArrowRightLeft className="h-4 w-4" />
                  Transfer stock
                </Button>
              </PermissionGate>
            ) : null}
            <PermissionGate permission={PERMISSIONS.BRANCH_MANAGE}>
              <Button type="button" onClick={openCreate}>
                <Plus className="h-4 w-4" />
                Add branch
              </Button>
            </PermissionGate>
          </div>
        )}
      />

      <Tabs defaultValue="branches">
        <TabsList>
          <TabsTrigger value="branches">Branches</TabsTrigger>
          <TabsTrigger value="stock" disabled={!multiBranch}>Stock by branch</TabsTrigger>
          <TabsTrigger value="transfers" disabled={!multiBranch}>Transfers</TabsTrigger>
        </TabsList>

        <TabsContent value="branches">
          <Card>
            <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3 space-y-0">
              <div>
                <CardTitle className="text-base">Branch-wise figures</CardTitle>
                <CardDescription>{multiBranch ? 'Sales and purchases recorded at each branch in the period.' : 'Add a second branch to see figures split by location.'}</CardDescription>
              </div>
              {multiBranch ? (
                <div className="flex items-center gap-2 text-sm">
                  <Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} className="h-8 w-auto" aria-label="From date" />
                  <span className="text-muted-foreground">to</span>
                  <Input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} className="h-8 w-auto" aria-label="To date" />
                </div>
              ) : null}
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Branch</TableHead>
                    <TableHead>Location</TableHead>
                    <TableHead>Status</TableHead>
                    {multiBranch ? (
                      <>
                        <TableHead className="text-right">Invoices</TableHead>
                        <TableHead className="text-right">Sales</TableHead>
                        <TableHead className="text-right">Purchases</TableHead>
                        <TableHead className="text-right">Users</TableHead>
                        <TableHead className="text-right">Products in stock</TableHead>
                      </>
                    ) : null}
                    <TableHead className="text-right"><span className="sr-only">Actions</span></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {branches.map((branch) => {
                    const s = summary.find((row) => row.branchId === branch.id);
                    return (
                      <TableRow key={branch.id}>
                        <TableCell>
                          <span className="flex items-center gap-2 font-medium">
                            <Building2 className="h-4 w-4 text-muted-foreground" aria-hidden />
                            {branch.branchName}
                            {branch.main ? <Badge variant="secondary">Main</Badge> : null}
                          </span>
                          <span className="block text-xs text-muted-foreground">{branch.branchCode}</span>
                        </TableCell>
                        <TableCell className="text-sm text-muted-foreground">{[branch.city, branch.stateCode].filter(Boolean).join(', ') || '—'}</TableCell>
                        <TableCell><Badge variant={branch.status === 'ACTIVE' ? 'success' : 'destructive'}>{branch.status === 'ACTIVE' ? 'Active' : 'Inactive'}</Badge></TableCell>
                        {multiBranch ? (
                          <>
                            <TableCell className="text-right tabular">{s?.invoiceCount ?? 0}</TableCell>
                            <TableCell className="text-right tabular">₹{s?.salesDisplay ?? '0.00'}</TableCell>
                            <TableCell className="text-right tabular">₹{s?.purchasesDisplay ?? '0.00'}</TableCell>
                            <TableCell className="text-right tabular">{s?.userCount ?? 0}</TableCell>
                            <TableCell className="text-right tabular">{s?.productsInStock ?? 0}</TableCell>
                          </>
                        ) : null}
                        <TableCell className="text-right">
                          <PermissionGate permission={PERMISSIONS.BRANCH_MANAGE}>
                            <Button type="button" variant="ghost" size="sm" onClick={() => openEdit(branch)}>
                              <Pencil className="h-4 w-4" />
                              Edit
                            </Button>
                          </PermissionGate>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="stock">
          <Card>
            <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3 space-y-0">
              <div>
                <CardTitle className="text-base">Stock by branch</CardTitle>
                <CardDescription>The shop&apos;s stock split by where it is. A negative figure means goods left a branch that never received them - record the transfer that happened.</CardDescription>
              </div>
              <div className="flex items-center gap-2">
                <Select value={stockBranch} onValueChange={setStockBranch}>
                  <SelectTrigger className="h-8 w-44"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All branches</SelectItem>
                    {branches.map((b) => <SelectItem key={b.id} value={String(b.id)}>{b.branchName}</SelectItem>)}
                  </SelectContent>
                </Select>
                <div className="relative">
                  <Search className="pointer-events-none absolute left-2 top-2 h-4 w-4 text-muted-foreground" aria-hidden />
                  <Input value={stockSearch} onChange={(e) => setStockSearch(e.target.value)} placeholder="Search product" className="h-8 w-48 pl-8" />
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {stock.length === 0 ? (
                <EmptyState icon={Building2} title="No stock recorded by branch" description="Purchases, sales and transfers build this up as they happen." />
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Branch</TableHead>
                      <TableHead>Product</TableHead>
                      <TableHead className="text-right">On hand</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {stock.map((row) => (
                      <TableRow key={`${row.branchId}-${row.productId}`}>
                        <TableCell>{row.branchName}</TableCell>
                        <TableCell>
                          <span className="block">{row.productName}</span>
                          <span className="block text-xs text-muted-foreground">{row.productCode}</span>
                        </TableCell>
                        <TableCell className={`text-right tabular ${row.quantityOnHand < 0 ? 'text-destructive' : ''}`}>
                          {row.quantityOnHand} {row.unit}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="transfers">
          <Card>
            <CardHeader><CardTitle className="text-base">Recent transfers</CardTitle></CardHeader>
            <CardContent>
              {transfers.length === 0 ? (
                <EmptyState icon={ArrowRightLeft} title="No transfers yet" description="Move stock between branches with the Transfer stock button." />
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Number</TableHead>
                      <TableHead>From</TableHead>
                      <TableHead>To</TableHead>
                      <TableHead>Items</TableHead>
                      <TableHead>When</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {transfers.map((t) => (
                      <TableRow key={t.id}>
                        <TableCell className="font-medium">{t.transferNumber}</TableCell>
                        <TableCell>{t.fromBranchName}</TableCell>
                        <TableCell>{t.toBranchName}</TableCell>
                        <TableCell className="text-sm">{t.items.map((i) => `${i.productName} × ${i.quantity}`).join(', ')}</TableCell>
                        <TableCell className="whitespace-nowrap text-sm">{formatDateTime(t.createdAt)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{editing ? `Edit ${editing.branchName}` : 'Add branch'}</DialogTitle></DialogHeader>
          <div className="grid gap-3 sm:grid-cols-2">
            <FormField id="branchCode" label="Code" required>
              <Input id="branchCode" value={form.branchCode} maxLength={20} disabled={editing?.main}
                     onChange={(e) => setForm({ ...form, branchCode: e.target.value.toUpperCase() })} placeholder="GODOWN" />
            </FormField>
            <FormField id="branchName" label="Name" required>
              <Input id="branchName" value={form.branchName} maxLength={150} onChange={(e) => setForm({ ...form, branchName: e.target.value })} />
            </FormField>
            <FormField id="branchAddress" label="Address" className="sm:col-span-2">
              <Input id="branchAddress" value={form.addressLine1 ?? ''} maxLength={255} onChange={(e) => setForm({ ...form, addressLine1: e.target.value })} />
            </FormField>
            <FormField id="branchCity" label="City">
              <Input id="branchCity" value={form.city ?? ''} maxLength={100} onChange={(e) => setForm({ ...form, city: e.target.value })} />
            </FormField>
            <FormField id="branchState" label="State code" hint="2-digit GST state code">
              <Input id="branchState" value={form.stateCode ?? ''} maxLength={2} inputMode="numeric" onChange={(e) => setForm({ ...form, stateCode: e.target.value })} />
            </FormField>
            <FormField id="branchPincode" label="PIN code">
              <Input id="branchPincode" value={form.pincode ?? ''} maxLength={6} inputMode="numeric" onChange={(e) => setForm({ ...form, pincode: e.target.value })} />
            </FormField>
            <FormField id="branchPhone" label="Phone">
              <Input id="branchPhone" value={form.phone ?? ''} maxLength={10} inputMode="tel" onChange={(e) => setForm({ ...form, phone: e.target.value })} />
            </FormField>
            {editing && !editing.main ? (
              <FormField id="branchStatus" label="Status">
                <Select value={form.status ?? 'ACTIVE'} onValueChange={(v) => setForm({ ...form, status: v as 'ACTIVE' | 'INACTIVE' })}>
                  <SelectTrigger id="branchStatus"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ACTIVE">Active</SelectItem>
                    <SelectItem value="INACTIVE">Inactive</SelectItem>
                  </SelectContent>
                </Select>
              </FormField>
            ) : null}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setFormOpen(false)} disabled={saving}>Cancel</Button>
            <Button type="button" onClick={() => void saveBranch()} loading={saving} disabled={!form.branchCode.trim() || !form.branchName.trim()}>
              {editing ? 'Save changes' : 'Create branch'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={transferOpen} onOpenChange={(open) => { setTransferOpen(open); if (!open) { setProductQuery(''); setProductHits([]); } }}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader><DialogTitle>Transfer stock</DialogTitle></DialogHeader>
          <p className="text-sm text-muted-foreground">Moves goods between two branches. The shop&apos;s total stock does not change; the source branch must hold what you move.</p>
          <div className="grid gap-3 sm:grid-cols-2">
            <FormField id="transferFrom" label="From" required>
              <Select value={transferFrom} onValueChange={setTransferFrom}>
                <SelectTrigger id="transferFrom"><SelectValue placeholder="Source branch" /></SelectTrigger>
                <SelectContent>
                  {branches.filter((b) => b.status === 'ACTIVE').map((b) => <SelectItem key={b.id} value={String(b.id)}>{b.branchName}</SelectItem>)}
                </SelectContent>
              </Select>
            </FormField>
            <FormField id="transferTo" label="To" required>
              <Select value={transferTo} onValueChange={setTransferTo}>
                <SelectTrigger id="transferTo"><SelectValue placeholder="Destination branch" /></SelectTrigger>
                <SelectContent>
                  {branches.filter((b) => b.status === 'ACTIVE' && String(b.id) !== transferFrom).map((b) => <SelectItem key={b.id} value={String(b.id)}>{b.branchName}</SelectItem>)}
                </SelectContent>
              </Select>
            </FormField>
          </div>
          <FormField id="transferProduct" label="Add product">
            <div className="relative">
              <Input id="transferProduct" value={productQuery} onChange={(e) => setProductQuery(e.target.value)} placeholder="Type at least 2 characters" autoComplete="off" />
              {productHits.length > 0 ? (
                <ul className="absolute z-10 mt-1 max-h-48 w-full overflow-auto rounded-md border bg-popover p-1 text-sm shadow-md" role="listbox">
                  {productHits.map((p) => (
                    <li key={p.id}>
                      <button type="button" className="w-full rounded-sm px-2 py-1.5 text-left hover:bg-accent" onClick={() => addLine(p)}>
                        {p.productName} <span className="text-xs text-muted-foreground">{p.productCode}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
          </FormField>
          {transferLines.length > 0 ? (
            <ul className="divide-y rounded-md border text-sm">
              {transferLines.map((line, index) => (
                <li key={line.product.id} className="flex items-center justify-between gap-2 px-3 py-2">
                  <span className="min-w-0 flex-1 truncate">{line.product.productName}</span>
                  <Input type="number" min="0.0001" step="any" inputMode="decimal" value={line.quantity} className="h-8 w-24"
                         aria-label={`Quantity of ${line.product.productName}`}
                         onChange={(e) => setTransferLines((lines) => lines.map((l, i) => i === index ? { ...l, quantity: e.target.value } : l))} />
                  <Button type="button" variant="ghost" size="sm" onClick={() => setTransferLines((lines) => lines.filter((_, i) => i !== index))}>Remove</Button>
                </li>
              ))}
            </ul>
          ) : null}
          <FormField id="transferNotes" label="Notes">
            <Input id="transferNotes" value={transferNotes} maxLength={500} onChange={(e) => setTransferNotes(e.target.value)} />
          </FormField>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setTransferOpen(false)} disabled={transferring}>Cancel</Button>
            <Button type="button" onClick={() => void submitTransfer()} loading={transferring}
                    disabled={!transferFrom || !transferTo || transferFrom === transferTo || transferLines.length === 0}>
              <ArrowRightLeft className="h-4 w-4" />
              Transfer
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <UpgradeDialog details={gate.details} onClose={gate.close} />
    </>
  );
}
