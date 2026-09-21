import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, Loader2, Scale } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
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
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { FormField } from '@/shared/components/FormField';
import { ApiError } from '@/shared/types/api';
import { formatDateTime } from '@/shared/lib/utils';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { INVOICE_ROUTES } from '@/modules/invoice/constants';
import { customerLedgerService } from '../services/customerLedgerService';
import type {
  AgeingResponse, LedgerBalanceResponse, LedgerEntryType, StatementResponse,
} from '../types/ledger';

const ENTRY_LABEL: Record<LedgerEntryType, string> = {
  INVOICE: 'Invoice',
  PAYMENT: 'Payment',
  SALES_RETURN: 'Sales return',
  INVOICE_CANCELLATION: 'Invoice cancelled',
  ADJUSTMENT: 'Adjustment',
};

const ENTRY_VARIANT: Record<LedgerEntryType, 'default' | 'success' | 'info' | 'warning' | 'secondary'> = {
  INVOICE: 'default',
  PAYMENT: 'success',
  SALES_RETURN: 'info',
  INVOICE_CANCELLATION: 'warning',
  ADJUSTMENT: 'secondary',
};

function isoDaysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

/**
 * CR-091 Phase 4. The customer's account as the ledger records it - an
 * append-only debit/credit history - never a mutable balance column. The
 * balance, statement and ageing are all read from the same server rows,
 * so the three views cannot disagree with each other.
 */
export function CustomerLedgerPanel({ customerId }: { customerId: number }) {
  const toast = useToast();
  const [balance, setBalance] = useState<LedgerBalanceResponse | null>(null);
  const [ageing, setAgeing] = useState<AgeingResponse | null>(null);
  const [statement, setStatement] = useState<StatementResponse | null>(null);
  const [from, setFrom] = useState(() => isoDaysAgo(90));
  const [to, setTo] = useState(() => isoDaysAgo(0));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const [adjustOpen, setAdjustOpen] = useState(false);
  const [adjustAmount, setAdjustAmount] = useState('');
  const [adjustDirection, setAdjustDirection] = useState<'debit' | 'credit'>('credit');
  const [adjustReason, setAdjustReason] = useState('');
  const [adjusting, setAdjusting] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [balanceData, ageingData, statementData] = await Promise.all([
        customerLedgerService.balance(customerId),
        customerLedgerService.ageing(customerId),
        customerLedgerService.statement(customerId, from, to),
      ]);
      setBalance(balanceData);
      setAgeing(ageingData);
      setStatement(statementData);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : new ApiError({ message: 'Could not load the ledger', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [customerId, from, to]);

  useEffect(() => { void reload(); }, [reload]);

  const submitAdjustment = async () => {
    const rupees = Number(adjustAmount);
    if (!Number.isFinite(rupees) || rupees <= 0 || !adjustReason.trim()) return;
    setAdjusting(true);
    try {
      await customerLedgerService.adjust(customerId, {
        amountPaise: Math.round(rupees * 100),
        debit: adjustDirection === 'debit',
        reason: adjustReason.trim(),
      });
      toast.success('Adjustment recorded.');
      setAdjustOpen(false);
      setAdjustAmount('');
      setAdjustReason('');
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not record the adjustment.');
    } finally {
      setAdjusting(false);
    }
  };

  if (loading && !balance) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin" aria-label="Loading ledger" />
      </div>
    );
  }
  if (error) return <ErrorState error={error} onRetry={() => void reload()} />;
  if (!balance || !ageing || !statement) return null;

  const owes = balance.balancePaise > 0;
  const inAdvance = balance.balancePaise < 0;

  return (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Outstanding</CardTitle></CardHeader>
          <CardContent>
            <div className={`text-2xl font-semibold tabular ${owes ? 'text-destructive' : inAdvance ? 'text-success' : ''}`}>
              {inAdvance ? '-' : ''}₹{balance.balanceDisplay.replace(/^-/, '')}
            </div>
            <p className="mt-1 text-xs text-muted-foreground">
              {owes ? 'Customer owes the shop' : inAdvance ? 'Customer is in advance' : 'Settled'}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Ageing (as of {ageing.asOf})</CardTitle></CardHeader>
          <CardContent className="space-y-1 text-sm">
            {ageing.buckets.map((bucket) => (
              <div key={bucket.label} className="flex justify-between">
                <span className="text-muted-foreground">{bucket.label}{bucket.invoiceCount ? ` (${bucket.invoiceCount})` : ''}</span>
                <span className="tabular">₹{bucket.display}</span>
              </div>
            ))}
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Lifetime</CardTitle></CardHeader>
          <CardContent className="space-y-1 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Billed</span><span className="tabular">₹{balance.totalDebitDisplay}</span></div>
            <div className="flex justify-between"><span className="text-muted-foreground">Received / credited</span><span className="tabular">₹{balance.totalCreditDisplay}</span></div>
            <PermissionGate permission={PERMISSIONS.PAYMENT_MANAGE}>
              <Button type="button" variant="outline" size="sm" className="mt-2 w-full" onClick={() => setAdjustOpen(true)}>
                <Scale className="h-4 w-4" />
                Manual adjustment
              </Button>
            </PermissionGate>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3 space-y-0">
          <CardTitle className="text-base">Statement</CardTitle>
          <div className="flex items-center gap-2 text-sm">
            <Input type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} className="h-8 w-auto" aria-label="From date" />
            <span className="text-muted-foreground">to</span>
            <Input type="date" value={to} min={from} onChange={(event) => setTo(event.target.value)} className="h-8 w-auto" aria-label="To date" />
          </div>
        </CardHeader>
        <CardContent>
          {statement.entries.length === 0 ? (
            <EmptyState icon={BookOpen} title="No ledger entries in this period" description="Invoices, payments, returns and cancellations for this customer appear here." />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Entry</TableHead>
                  <TableHead>Reference</TableHead>
                  <TableHead className="text-right">Debit</TableHead>
                  <TableHead className="text-right">Credit</TableHead>
                  <TableHead className="text-right">Balance</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow className="text-muted-foreground">
                  <TableCell colSpan={5}>Opening balance on {statement.from}</TableCell>
                  <TableCell className="text-right tabular">₹{statement.openingBalanceDisplay}</TableCell>
                </TableRow>
                {statement.entries.map((entry) => (
                  <TableRow key={entry.id}>
                    <TableCell className="whitespace-nowrap">{formatDateTime(entry.entryDate)}</TableCell>
                    <TableCell>
                      <Badge variant={ENTRY_VARIANT[entry.entryType]}>{ENTRY_LABEL[entry.entryType]}</Badge>
                      {entry.notes ? <div className="mt-0.5 text-xs text-muted-foreground">{entry.notes}</div> : null}
                    </TableCell>
                    <TableCell>
                      {entry.referenceType === 'INVOICE' && entry.referenceId ? (
                        <Link to={INVOICE_ROUTES.detail(entry.referenceId)} className="text-primary hover:underline">{entry.referenceNumber}</Link>
                      ) : (entry.referenceNumber ?? '—')}
                    </TableCell>
                    <TableCell className="text-right tabular">{entry.debitPaise ? `₹${entry.debitDisplay}` : ''}</TableCell>
                    <TableCell className="text-right tabular">{entry.creditPaise ? `₹${entry.creditDisplay}` : ''}</TableCell>
                    <TableCell className="text-right tabular">₹{entry.balanceDisplay}</TableCell>
                  </TableRow>
                ))}
                <TableRow className="font-semibold">
                  <TableCell colSpan={5}>Closing balance on {statement.to}</TableCell>
                  <TableCell className="text-right tabular">₹{statement.closingBalanceDisplay}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {ageing.invoices.length > 0 ? (
        <Card>
          <CardHeader><CardTitle className="text-base">Open invoices</CardTitle></CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Invoice</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead className="text-right">Age</TableHead>
                  <TableHead className="text-right">Outstanding</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {ageing.invoices.map((invoice) => (
                  <TableRow key={invoice.invoiceId}>
                    <TableCell><Link to={INVOICE_ROUTES.detail(invoice.invoiceId)} className="text-primary hover:underline">{invoice.invoiceNumber}</Link></TableCell>
                    <TableCell>{invoice.invoiceDate}</TableCell>
                    <TableCell className="text-right tabular">{invoice.ageDays} d</TableCell>
                    <TableCell className="text-right tabular">₹{invoice.outstandingDisplay}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      ) : null}

      <Dialog open={adjustOpen} onOpenChange={setAdjustOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Manual ledger adjustment</DialogTitle></DialogHeader>
          <p className="text-sm text-muted-foreground">
            Corrects the customer&apos;s balance without an invoice or payment. It is recorded in the activity log with your name and the reason.
          </p>
          <FormField id="adjustDirection" label="Direction" required>
            <Select value={adjustDirection} onValueChange={(value) => setAdjustDirection(value as 'debit' | 'credit')}>
              <SelectTrigger id="adjustDirection"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="credit">Customer owes less (credit)</SelectItem>
                <SelectItem value="debit">Customer owes more (debit)</SelectItem>
              </SelectContent>
            </Select>
          </FormField>
          <FormField id="adjustAmount" label="Amount (₹)" required>
            <Input id="adjustAmount" type="number" min="0.01" step="0.01" inputMode="decimal"
                   value={adjustAmount} onChange={(event) => setAdjustAmount(event.target.value)} />
          </FormField>
          <FormField id="adjustReason" label="Reason" required>
            <Input id="adjustReason" maxLength={255} placeholder="e.g. Rounding-off agreed with customer"
                   value={adjustReason} onChange={(event) => setAdjustReason(event.target.value)} />
          </FormField>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setAdjustOpen(false)} disabled={adjusting}>Cancel</Button>
            <Button type="button" onClick={() => void submitAdjustment()} loading={adjusting}
                    disabled={!adjustReason.trim() || !(Number(adjustAmount) > 0)}>
              Record adjustment
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
