import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { ArrowLeft, Loader2, Plus, Wallet, X } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/components/ui/card';
import { Input } from '@/shared/components/ui/input';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/shared/components/ui/dialog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/components/ui/table';
import { Badge } from '@/shared/components/ui/badge';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { ApiError } from '@/shared/types/api';
import { LABOUR_ROUTES } from '../constants';
import { workerService } from '../services/workerService';
import { attendanceService } from '../services/attendanceService';
import { workerPaymentService } from '../services/workerPaymentService';
import { WorkerPaymentForm } from '../forms/WorkerPaymentForm';
import { WorkerStatusBadge } from '../components/WorkerStatusBadge';
import { AttendanceStatusBadge } from '../components/AttendanceStatusBadge';
import type {
  WorkerAttendanceResponse, WorkerPaymentResponse, WorkerResponse, WorkerWageSummaryResponse,
} from '../types';
import type { WorkerPaymentValues } from '../validation/schemas';

export function WorkerDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const toast = useToast();
  const validId = Boolean(params.id) && !Number.isNaN(id);

  const [worker, setWorker] = useState<WorkerResponse | null>(null);
  const [summary, setSummary] = useState<WorkerWageSummaryResponse | null>(null);
  const [payments, setPayments] = useState<WorkerPaymentResponse[]>([]);
  const [attendance, setAttendance] = useState<WorkerAttendanceResponse[]>([]);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [recording, setRecording] = useState(false);
  const [cancelling, setCancelling] = useState<WorkerPaymentResponse | null>(null);

  const load = useCallback(async () => {
    if (!validId) return;
    setLoading(true);
    setError(null);
    try {
      const [workerData, summaryData, paymentsData, attendanceData] = await Promise.all([
        workerService.get(id),
        workerPaymentService.wageSummary(id, fromDate || undefined, toDate || undefined),
        workerPaymentService.listForWorker(id),
        attendanceService.historyForWorker(id, fromDate || undefined, toDate || undefined),
      ]);
      setWorker(workerData);
      setSummary(summaryData);
      setPayments(paymentsData);
      setAttendance(attendanceData);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, validId, fromDate, toDate]);

  useEffect(() => { void load(); }, [load]);

  if (!validId) return <Navigate to={LABOUR_ROUTES.workers} replace />;

  const handleRecordPayment = async (values: WorkerPaymentValues) => {
    await workerPaymentService.create({
      workerId: id,
      amountPaise: Math.round(Number(values.amountRupees) * 100),
      paymentDate: values.paymentDate,
      paymentMethod: values.paymentMethod,
      notes: values.notes || null,
    });
    setRecording(false);
    toast.success('Payment recorded.');
    await load();
  };

  const handleCancelPayment = async () => {
    if (!cancelling) return;
    try {
      await workerPaymentService.cancel(cancelling.id);
      toast.success('Payment cancelled.');
      await load();
    } catch (caught) {
      toast.error(caught, 'Could not cancel this payment.');
      throw caught;
    }
  };

  if (error) return <Card><ErrorState error={error} onRetry={load} /></Card>;
  if (loading || !worker || !summary) {
    return (
      <div className="flex justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
      </div>
    );
  }

  return (
    <>
      <Link to={LABOUR_ROUTES.workers}
            className="mb-1 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" />
        Workers
      </Link>

      <PageHeader
        title={worker.name}
        description={`${worker.roleTitle ?? 'No role set'} · ₹${worker.dailyRateDisplay}/day${worker.mobileNo ? ` · ${worker.mobileNo}` : ''}`}
        actions={
          <div className="flex items-center gap-2">
            <WorkerStatusBadge status={worker.status} />
            <PermissionGate permission={PERMISSIONS.LABOUR_MANAGE}>
              <Button onClick={() => setRecording(true)}>
                <Plus className="h-4 w-4" />
                <span className="hidden sm:inline">Record payment</span>
              </Button>
            </PermissionGate>
          </div>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <Input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)}
               className="sm:w-40" aria-label="From date" />
        <Input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)}
               className="sm:w-40" aria-label="To date" />
        {fromDate || toDate ? (
          <Button variant="ghost" size="sm" onClick={() => { setFromDate(''); setToDate(''); }}>
            Clear dates
          </Button>
        ) : null}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardContent className="py-4">
            <p className="text-sm text-muted-foreground">Wages earned</p>
            <p className="tabular text-xl font-semibold">₹{summary.wageEarnedDisplay}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-4">
            <p className="text-sm text-muted-foreground">Paid</p>
            <p className="tabular text-xl font-semibold">₹{summary.paidDisplay}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="py-4">
            {/* A negative balance is normal, not an error: paying a day-wage worker an advance is ordinary practice. Label it as an advance rather than showing "Balance owed: ₹-500.00". */}
            <p className="text-sm text-muted-foreground">
              {summary.balancePaise < 0 ? 'Paid in advance' : 'Balance owed'}
            </p>
            <p className={`tabular text-xl font-semibold ${summary.balancePaise > 0 ? 'text-warning' : ''}`}>
              ₹{summary.balancePaise < 0
                ? summary.balanceDisplay.replace('-', '')
                : summary.balanceDisplay}
            </p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader><CardTitle className="text-base">Payment history</CardTitle></CardHeader>
        <CardContent className="p-0">
          {payments.length === 0 ? (
            <EmptyState icon={Wallet} title="No payments recorded yet" description="Record a payment once wages are paid out." />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Method</TableHead>
                  <TableHead className="hidden sm:table-cell">Notes</TableHead>
                  <TableHead className="text-right">Amount</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {payments.map((row) => {
                  const cancelled = row.status === 'CANCELLED';
                  return (
                    <TableRow key={row.id} className={cancelled ? 'text-muted-foreground' : undefined}>
                      <TableCell className="tabular">{row.paymentDate}</TableCell>
                      <TableCell>
                        {row.paymentMethod}
                        {cancelled ? <Badge variant="secondary" className="ml-2">Cancelled</Badge> : null}
                      </TableCell>
                      <TableCell className="hidden max-w-xs truncate sm:table-cell">{row.notes ?? '—'}</TableCell>
                      <TableCell className={`tabular text-right ${cancelled ? 'line-through' : ''}`}>
                        ₹{row.amountDisplay}
                      </TableCell>
                      <TableCell>
                        {!cancelled ? (
                          <PermissionGate permission={PERMISSIONS.LABOUR_MANAGE}>
                            <Button variant="ghost" size="icon" className="h-8 w-8"
                                    aria-label={`Cancel payment of ₹${row.amountDisplay}`}
                                    onClick={() => setCancelling(row)}>
                              <X className="h-4 w-4" />
                            </Button>
                          </PermissionGate>
                        ) : null}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-base">Attendance history</CardTitle></CardHeader>
        <CardContent className="p-0">
          {attendance.length === 0 ? (
            <EmptyState icon={Wallet} title="No attendance marked yet" description="Mark attendance from the Attendance page." />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="hidden sm:table-cell">Project</TableHead>
                  <TableHead className="text-right">Wage</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {attendance.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell className="tabular">{row.attendanceDate}</TableCell>
                    <TableCell><AttendanceStatusBadge status={row.status} /></TableCell>
                    <TableCell className="hidden sm:table-cell">{row.projectName ?? '—'}</TableCell>
                    <TableCell className="tabular text-right">₹{row.wageDisplay}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog open={recording} onOpenChange={(open) => !open && setRecording(false)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader><DialogTitle>Record payment to {worker.name}</DialogTitle></DialogHeader>
          <WorkerPaymentForm onSubmit={handleRecordPayment} onCancel={() => setRecording(false)} />
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={cancelling !== null}
        onOpenChange={(open) => !open && setCancelling(null)}
        title="Cancel this payment?"
        description={cancelling
          ? `₹${cancelling.amountDisplay} paid on ${cancelling.paymentDate} stays in the history for the record, but stops counting towards what this worker has been paid.`
          : ''}
        confirmLabel="Cancel payment"
        destructive
        onConfirm={handleCancelPayment}
      />
    </>
  );
}
