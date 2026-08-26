import { useCallback, useEffect, useMemo, useState } from 'react';
import { CalendarCheck, Save } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import { Input } from '@/shared/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { ApiError } from '@/shared/types/api';
import { projectService } from '@/modules/project/services/projectService';
import type { ProjectSummaryResponse } from '@/modules/project/types';
import { ATTENDANCE_STATUS_OPTIONS } from '../constants';
import { workerService } from '../services/workerService';
import { attendanceService } from '../services/attendanceService';
import type { AttendanceEntryRequest, AttendanceStatus, WorkerResponse } from '../types';

const NO_PROJECT = '__none__';

const todayIso = () => new Date().toISOString().slice(0, 10);

interface RowState {
  /** null = not marked yet. Deliberately NOT defaulted to PRESENT: an unmarked worker must never be saved as a full day's wage just because the page was opened and saved. */
  status: AttendanceStatus | null;
  projectId: number | null;
}

export function AttendancePage() {
  const toast = useToast();

  const [date, setDate] = useState(todayIso);
  const [workers, setWorkers] = useState<WorkerResponse[]>([]);
  const [projects, setProjects] = useState<ProjectSummaryResponse[]>([]);
  const [rows, setRows] = useState<Record<number, RowState>>({});
  /** What was loaded from the server, to diff against for unsaved-change detection. */
  const [savedRows, setSavedRows] = useState<Record<number, RowState>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [projectsFailed, setProjectsFailed] = useState(false);
  const [pendingDate, setPendingDate] = useState<string | null>(null);

  useEffect(() => {
    projectService.search({ size: 100 })
      .then((page) => { setProjects(page.content); setProjectsFailed(false); })
      .catch(() => { setProjects([]); setProjectsFailed(true); });
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [activeWorkers, existingMarks] = await Promise.all([
        workerService.listActive(),
        attendanceService.forDate(date),
      ]);

      // A worker deactivated after a past date was marked is no longer in
      // listActive(), but their mark for that date still exists and must stay
      // visible and correctable - otherwise a wrong historical mark becomes
      // unreachable from this page.
      const activeIds = new Set(activeWorkers.map((worker) => worker.id));
      const alreadyMarked: WorkerResponse[] = existingMarks
        .filter((mark) => !activeIds.has(mark.workerId))
        .map((mark) => ({
          id: mark.workerId,
          name: mark.workerName,
          mobileNo: null,
          roleTitle: null,
          dailyRatePaise: 0,
          dailyRateDisplay: '0.00',
          status: 'INACTIVE',
          createdAt: '',
        }));

      const visible = [...activeWorkers, ...alreadyMarked]
        .sort((a, b) => a.name.localeCompare(b.name));
      setWorkers(visible);

      const marksByWorker = new Map(existingMarks.map((mark) => [mark.workerId, mark]));
      const nextRows: Record<number, RowState> = {};
      visible.forEach((worker) => {
        const existing = marksByWorker.get(worker.id);
        nextRows[worker.id] = {
          status: existing?.status ?? null,
          projectId: existing?.projectId ?? null,
        };
      });
      setRows(nextRows);
      setSavedRows(nextRows);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught
        : new ApiError({ message: 'Something went wrong', code: 'INTERNAL_ERROR', status: 500 }));
    } finally {
      setLoading(false);
    }
  }, [date]);

  useEffect(() => { void load(); }, [load]);

  const markedCount = useMemo(
    () => Object.values(rows).filter((row) => row.status !== null).length,
    [rows],
  );

  const dirty = useMemo(
    () => Object.keys(rows).some((key) => {
      const id = Number(key);
      return rows[id]?.status !== savedRows[id]?.status
        || rows[id]?.projectId !== savedRows[id]?.projectId;
    }),
    [rows, savedRows],
  );

  const setRow = (workerId: number, patch: Partial<RowState>) => {
    setRows((current) => ({ ...current, [workerId]: { ...current[workerId], ...patch } }));
  };

  const requestDateChange = (next: string) => {
    if (dirty) { setPendingDate(next); return; }
    setDate(next);
  };

  const handleSave = async () => {
    // Only workers actually marked are submitted. Sending every worker would
    // silently create a full day's wage for anyone left untouched.
    const entries: AttendanceEntryRequest[] = workers
      .filter((worker) => rows[worker.id]?.status != null)
      .map((worker) => ({
        workerId: worker.id,
        status: rows[worker.id].status as AttendanceStatus,
        projectId: rows[worker.id].projectId ?? null,
      }));

    if (entries.length === 0) {
      toast.error(null, 'Mark at least one worker before saving.');
      return;
    }

    setSaving(true);
    try {
      await attendanceService.mark({ attendanceDate: date, entries });
      toast.success(`Attendance saved for ${entries.length} worker${entries.length === 1 ? '' : 's'}.`);
      await load();
    } catch (caught) {
      toast.error(caught, 'Could not save attendance.');
    } finally {
      setSaving(false);
    }
  };

  const projectSelect = (workerId: number, row: RowState) => (
    <Select
      value={row.projectId ? String(row.projectId) : NO_PROJECT}
      onValueChange={(value) => {
        if (value === '') return;
        setRow(workerId, { projectId: value === NO_PROJECT ? null : Number(value) });
      }}
    >
      <SelectTrigger className="w-full sm:w-56" aria-label="Project">
        <SelectValue placeholder="No project" />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={NO_PROJECT}>No project</SelectItem>
        {projects.map((project) => (
          <SelectItem key={project.id} value={String(project.id)}>{project.projectName}</SelectItem>
        ))}
      </SelectContent>
    </Select>
  );

  return (
    <>
      <PageHeader
        title="Attendance"
        description="Mark the crew's attendance for one day - present, absent or half day. Workers left unmarked are not saved."
        actions={
          <PermissionGate permission={PERMISSIONS.LABOUR_MANAGE}>
            <Button onClick={handleSave} loading={saving} disabled={loading || markedCount === 0}>
              <Save className="h-4 w-4" />
              <span className="hidden sm:inline">
                Save{markedCount > 0 ? ` (${markedCount})` : ''}
              </span>
            </Button>
          </PermissionGate>
        }
      />

      <div className="flex flex-wrap items-center gap-3">
        <Input
          type="date"
          value={date}
          max={todayIso()}
          onChange={(e) => requestDateChange(e.target.value)}
          className="w-44"
          aria-label="Attendance date"
        />
        {dirty ? (
          <span className="text-sm text-warning">Unsaved changes</span>
        ) : null}
      </div>

      {projectsFailed ? (
        <p className="text-sm text-muted-foreground">
          Projects could not be loaded, so attendance cannot be assigned to a project right now.
          Everything else on this page still works.
        </p>
      ) : null}

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={load} />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Worker</TableHead>
                <TableHead className="w-64">Status</TableHead>
                <TableHead className="hidden sm:table-cell">Project</TableHead>
              </TableRow>
            </TableHeader>

            {loading ? (
              <TableSkeleton columns={3} rows={5} />
            ) : (
              <TableBody>
                {workers.map((worker) => {
                  const row = rows[worker.id] ?? { status: null, projectId: null };
                  return (
                    <TableRow key={worker.id}>
                      <TableCell className="font-medium">
                        {worker.name}
                        {worker.roleTitle ? (
                          <span className="ml-1.5 text-xs text-muted-foreground">({worker.roleTitle})</span>
                        ) : null}
                        {worker.status === 'INACTIVE' ? (
                          <span className="ml-1.5 text-xs text-muted-foreground">(deactivated)</span>
                        ) : null}
                        {/* Below sm the Project column is hidden, so the picker moves inline here - a site supervisor marking attendance on a phone must still be able to attribute it to a project. */}
                        <div className="mt-2 sm:hidden">{projectSelect(worker.id, row)}</div>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-1">
                          {ATTENDANCE_STATUS_OPTIONS.map((option) => (
                            <Button
                              key={option.value}
                              type="button"
                              size="sm"
                              variant={row.status === option.value ? 'default' : 'outline'}
                              aria-pressed={row.status === option.value}
                              onClick={() => setRow(worker.id, {
                                // Clicking the already-selected status clears it,
                                // so a mis-click can be undone without saving.
                                status: row.status === option.value ? null : option.value,
                              })}
                            >
                              {option.label}
                            </Button>
                          ))}
                        </div>
                      </TableCell>
                      <TableCell className="hidden sm:table-cell">
                        {projectSelect(worker.id, row)}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            )}
          </Table>
        )}

        {!loading && !error && workers.length === 0 ? (
          <EmptyState
            icon={CalendarCheck}
            title="No active workers"
            description="Add a worker in the Workers page before marking attendance."
          />
        ) : null}
      </Card>

      <ConfirmDialog
        open={pendingDate !== null}
        onOpenChange={(open) => !open && setPendingDate(null)}
        title="Discard unsaved attendance?"
        description="You have marked attendance that has not been saved. Changing the date will discard it."
        confirmLabel="Discard and change date"
        destructive
        onConfirm={async () => {
          if (pendingDate) setDate(pendingDate);
          setPendingDate(null);
        }}
      />
    </>
  );
}
