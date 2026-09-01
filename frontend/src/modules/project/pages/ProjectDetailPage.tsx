import { useCallback, useEffect, useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import {
  Calculator, Loader2, Pencil, Plus, RefreshCcw, Trash2,
} from 'lucide-react';
import { BackLink } from '@/shared/components/BackLink';
import { Button } from '@/shared/components/ui/button';
import {
  Card, CardContent, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import {
  Tabs, TabsContent, TabsList, TabsTrigger,
} from '@/shared/components/ui/tabs';
import { PageHeader } from '@/shared/components/PageHeader';
import { ErrorState } from '@/shared/components/ErrorState';
import { EmptyState } from '@/shared/components/EmptyState';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { ApiError } from '@/shared/types/api';
import { formatDateTime } from '@/shared/lib/utils';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { PROJECT_ROUTES } from '../constants';
import { projectService } from '../services/projectService';
import { ProjectStatusBadge, ProjectOutcomeBadge } from '../components/ProjectStatusBadge';
import { ProjectStatusChangeDialog } from '../forms/ProjectStatusChangeDialog';
import { ProjectMaterialFormDialog } from '../forms/ProjectMaterialFormDialog';
import { ProjectExpenseFormDialog } from '../forms/ProjectExpenseFormDialog';
import { ProjectPaymentFormDialog } from '../forms/ProjectPaymentFormDialog';
import { RooftopCalculatorDialog } from '../forms/RooftopCalculatorDialog';
import type {
  ProjectExpenseResponse, ProjectMaterialResponse, ProjectPaymentResponse, ProjectResponse,
} from '../types';

function useProjectDetail(id: number) {
  const [project, setProject] = useState<ProjectResponse | null>(null);
  const [materials, setMaterials] = useState<ProjectMaterialResponse[]>([]);
  const [expenses, setExpenses] = useState<ProjectExpenseResponse[]>([]);
  const [payments, setPayments] = useState<ProjectPaymentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [p, m, e, pay] = await Promise.all([
        projectService.get(id), projectService.materials(id), projectService.expenses(id), projectService.payments(id),
      ]);
      setProject(p);
      setMaterials(m);
      setExpenses(e);
      setPayments(pay);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { void reload(); }, [reload]);

  return {
    project, materials, expenses, payments, loading, error, reload,
  };
}

export function ProjectDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const navigate = useNavigate();
  const toast = useToast();

  const [changingStatus, setChangingStatus] = useState(false);
  const [addingMaterial, setAddingMaterial] = useState(false);
  const [addingExpense, setAddingExpense] = useState(false);
  const [addingPayment, setAddingPayment] = useState(false);
  const [showCalculator, setShowCalculator] = useState(false);
  const [removingMaterial, setRemovingMaterial] = useState<ProjectMaterialResponse | null>(null);
  const [removingExpense, setRemovingExpense] = useState<ProjectExpenseResponse | null>(null);

  const validId = Boolean(params.id) && !Number.isNaN(id);
  const {
    project, materials, expenses, payments, loading, error, reload,
  } = useProjectDetail(validId ? id : -1);

  if (!validId) return <Navigate to={PROJECT_ROUTES.list} replace />;
  if (loading) {
    return <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" /></div>;
  }
  if (error || !project) {
    return <ErrorState error={error ?? new ApiError({ message: 'Project not found', code: 'NOT_FOUND', status: 404 })} onRetry={reload} />;
  }

  return (
    <>
      <BackLink to={PROJECT_ROUTES.list} label="Projects" />

      <PageHeader
        title={project.projectName}
        description={`${project.projectNumber} · ${project.customerName} · ${project.workTypeName}`}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex flex-col items-end gap-1">
              <ProjectStatusBadge status={project.status} overdue={project.overdue} />
              {project.outcome ? <ProjectOutcomeBadge outcome={project.outcome} /> : null}
            </div>
            <PermissionGate permission={PERMISSIONS.PROJECT_MANAGE}>
              <Button variant="outline" onClick={() => setChangingStatus(true)}>
                <RefreshCcw className="h-4 w-4" /> <span className="hidden sm:inline">Change status</span>
              </Button>
              <Button variant="outline" onClick={() => navigate(PROJECT_ROUTES.edit(project.id))}>
                <Pencil className="h-4 w-4" /> <span className="hidden sm:inline">Edit</span>
              </Button>
            </PermissionGate>
          </div>
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader><CardTitle className="text-base">Overview</CardTitle></CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-2 text-sm">
            <div><span className="block text-muted-foreground">Customer</span>{project.customerName}</div>
            <div><span className="block text-muted-foreground">Work type</span>{project.workTypeName}</div>
            <div><span className="block text-muted-foreground">Site address</span>{project.siteAddress ?? '—'}</div>
            <div><span className="block text-muted-foreground">Manager</span>{project.managerUserName ?? '—'}</div>
            <div><span className="block text-muted-foreground">Start date</span>{project.startDate ?? '—'}</div>
            <div><span className="block text-muted-foreground">Expected completion</span>{project.expectedCompletionDate ?? '—'}</div>
            <div><span className="block text-muted-foreground">Customer deadline</span>{project.customerDeadline ?? '—'}</div>
            <div><span className="block text-muted-foreground">Actual completion</span>{project.actualCompletionDate ?? '—'}</div>
            {project.description ? <div className="sm:col-span-2"><span className="block text-muted-foreground">Description</span>{project.description}</div> : null}
            {project.notes ? <div className="sm:col-span-2"><span className="block text-muted-foreground">Notes</span>{project.notes}</div> : null}
            <div className="sm:col-span-2 text-xs text-muted-foreground">Created {formatDateTime(project.createdAt)}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle className="text-base">Financial summary</CardTitle></CardHeader>
          <CardContent className="space-y-2 text-sm">
            <div className="flex justify-between"><span className="text-muted-foreground">Project value</span><span className="tabular">₹{project.projectValueDisplay}</span></div>
            <div className="flex justify-between"><span className="text-muted-foreground">Material cost</span><span className="tabular">₹{project.totalMaterialCostDisplay}</span></div>
            <div className="flex justify-between"><span className="text-muted-foreground">Other expenses</span><span className="tabular">₹{project.totalExpenseCostDisplay}</span></div>
            <div className="flex justify-between font-medium"><span>Total cost</span><span className="tabular">₹{project.totalCostDisplay}</span></div>
            <div className={`flex justify-between font-semibold ${project.profitPositive ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'}`}>
              <span>{project.profitPositive ? 'Net profit' : 'Net loss'}</span>
              <span className="tabular">₹{project.netProfitDisplay}</span>
            </div>
            <div className="flex justify-between text-xs text-muted-foreground"><span>Margin</span><span className="tabular">{project.profitMarginPercentDisplay}%</span></div>
            <div className="my-2 h-px bg-border" />
            <div className="flex justify-between">
              <span className="text-muted-foreground">Labour cost (attendance)</span>
              <span className="tabular">₹{project.totalLabourCostDisplay}</span>
            </div>
            <div className="my-2 h-px bg-border" />
            <div className="flex justify-between"><span className="text-muted-foreground">Received</span><span className="tabular">₹{project.totalReceivedDisplay}</span></div>
            <div className="flex justify-between font-medium"><span>Balance receivable</span><span className="tabular">₹{project.balanceReceivableDisplay}</span></div>
          </CardContent>
        </Card>
      </div>

      <Tabs defaultValue="materials" className="mt-4">
        <TabsList>
          <TabsTrigger value="materials">Materials</TabsTrigger>
          <TabsTrigger value="expenses">Expenses</TabsTrigger>
          <TabsTrigger value="payments">Payments</TabsTrigger>
        </TabsList>

        <TabsContent value="materials" className="space-y-3">
          <div className="flex justify-end gap-2">
            <Button variant="outline" size="sm" onClick={() => setShowCalculator(true)}>
              <Calculator className="h-4 w-4" /> Rooftop calculator
            </Button>
            <PermissionGate permission={PERMISSIONS.PROJECT_MATERIAL_MANAGE}>
              <Button size="sm" onClick={() => setAddingMaterial(true)}><Plus className="h-4 w-4" /> Add material</Button>
            </PermissionGate>
          </div>
          <Card>
            {materials.length === 0 ? (
              <EmptyState icon={Plus} title="No materials added yet" description="Add the products this project consumes." />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Product</TableHead>
                    <TableHead>Supplier</TableHead>
                    <TableHead className="text-right">Qty (req / actual)</TableHead>
                    <TableHead className="text-right">Unit price</TableHead>
                    <TableHead className="text-right">Total cost</TableHead>
                    <PermissionGate permission={PERMISSIONS.PROJECT_MATERIAL_MANAGE}><TableHead className="w-10" /></PermissionGate>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {materials.map((m) => (
                    <TableRow key={m.id}>
                      <TableCell><span className="font-medium">{m.productName}</span><span className="block text-xs text-muted-foreground">{m.productCode}</span></TableCell>
                      <TableCell>{m.supplierName ?? '—'}</TableCell>
                      <TableCell className="tabular text-right">{m.quantityRequired ?? '—'} / {m.quantityActual ?? '—'} {m.unit}</TableCell>
                      <TableCell className="tabular text-right">₹{m.unitPriceDisplay}</TableCell>
                      <TableCell className="tabular text-right">₹{m.totalCostDisplay}</TableCell>
                      <PermissionGate permission={PERMISSIONS.PROJECT_MATERIAL_MANAGE}>
                        <TableCell>
                          <button type="button" onClick={() => setRemovingMaterial(m)} aria-label={`Remove ${m.productName}`}
                                  className="rounded-sm p-1 text-muted-foreground hover:text-destructive">
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </TableCell>
                      </PermissionGate>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Card>
        </TabsContent>

        <TabsContent value="expenses" className="space-y-3">
          <div className="flex justify-end">
            <PermissionGate permission={PERMISSIONS.PROJECT_MANAGE}>
              <Button size="sm" onClick={() => setAddingExpense(true)}><Plus className="h-4 w-4" /> Add expense</Button>
            </PermissionGate>
          </div>
          <Card>
            {expenses.length === 0 ? (
              <EmptyState icon={Plus} title="No expenses recorded yet" description="Labour, food, stay and petrol costs go here." />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Category</TableHead>
                    <TableHead>Paid to</TableHead>
                    <TableHead>Date</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                    <PermissionGate permission={PERMISSIONS.PROJECT_MANAGE}><TableHead className="w-10" /></PermissionGate>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {expenses.map((e) => (
                    <TableRow key={e.id}>
                      <TableCell>{e.category}</TableCell>
                      <TableCell>{e.paidTo ?? '—'}</TableCell>
                      <TableCell>{e.expenseDate}</TableCell>
                      <TableCell className="tabular text-right">₹{e.amountDisplay}</TableCell>
                      <PermissionGate permission={PERMISSIONS.PROJECT_MANAGE}>
                        <TableCell>
                          <button type="button" onClick={() => setRemovingExpense(e)} aria-label="Remove expense"
                                  className="rounded-sm p-1 text-muted-foreground hover:text-destructive">
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </TableCell>
                      </PermissionGate>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Card>
        </TabsContent>

        <TabsContent value="payments" className="space-y-3">
          <div className="flex justify-end">
            <PermissionGate permission={PERMISSIONS.PROJECT_MANAGE}>
              <Button size="sm" onClick={() => setAddingPayment(true)}><Plus className="h-4 w-4" /> Record payment</Button>
            </PermissionGate>
          </div>
          <Card>
            {payments.length === 0 ? (
              <EmptyState icon={Plus} title="No payments received yet" description="Amounts received against the project value go here." />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Date</TableHead>
                    <TableHead>Method</TableHead>
                    <TableHead>Notes</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {payments.map((p) => (
                    <TableRow key={p.id}>
                      <TableCell>{p.paymentDate}</TableCell>
                      <TableCell>{p.paymentMethod}</TableCell>
                      <TableCell>{p.notes ?? '—'}</TableCell>
                      <TableCell className="tabular text-right">₹{p.amountDisplay}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Card>
        </TabsContent>
      </Tabs>

      <ProjectStatusChangeDialog
        open={changingStatus}
        onOpenChange={setChangingStatus}
        currentStatus={project.status}
        onSubmit={async (request) => { await projectService.changeStatus(project.id, request); toast.success('Project status updated.'); await reload(); }}
      />

      <ProjectMaterialFormDialog
        open={addingMaterial}
        onOpenChange={setAddingMaterial}
        onSubmit={async (request) => { await projectService.addMaterial(project.id, request); toast.success('Material added.'); await reload(); }}
      />

      <ProjectExpenseFormDialog
        open={addingExpense}
        onOpenChange={setAddingExpense}
        onSubmit={async (request) => { await projectService.addExpense(project.id, request); toast.success('Expense added.'); await reload(); }}
      />

      <ProjectPaymentFormDialog
        open={addingPayment}
        onOpenChange={setAddingPayment}
        onSubmit={async (request) => { await projectService.addPayment(project.id, request); toast.success('Payment recorded.'); await reload(); }}
      />

      <RooftopCalculatorDialog open={showCalculator} onOpenChange={setShowCalculator} />

      <ConfirmDialog
        open={removingMaterial !== null}
        onOpenChange={(open) => !open && setRemovingMaterial(null)}
        title="Remove this material?"
        description={`"${removingMaterial?.productName ?? 'This material'}" will be removed from the project and its cost excluded from profitability.`}
        confirmLabel="Remove"
        destructive
        onConfirm={async () => {
          if (!removingMaterial) return;
          await projectService.removeMaterial(project.id, removingMaterial.id);
          toast.success('Material removed.');
          await reload();
        }}
      />

      <ConfirmDialog
        open={removingExpense !== null}
        onOpenChange={(open) => !open && setRemovingExpense(null)}
        title="Remove this expense?"
        description="This expense will be removed and excluded from profitability."
        confirmLabel="Remove"
        destructive
        onConfirm={async () => {
          if (!removingExpense) return;
          await projectService.removeExpense(project.id, removingExpense.id);
          toast.success('Expense removed.');
          await reload();
        }}
      />
    </>
  );
}
