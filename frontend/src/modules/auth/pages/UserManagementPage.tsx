import { useCallback, useEffect, useMemo, useState } from 'react';
import { KeyRound, MoreHorizontal, Pencil, Trash2, UserPlus, Users } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/shared/components/ui/dropdown-menu';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { TableSkeleton } from '@/shared/components/TableSkeleton';
import { Pagination } from '@/shared/components/Pagination';
import { SearchInput } from '@/shared/components/SearchInput';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { formatDateTime } from '@/shared/lib/utils';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS, USER_STATUS_OPTIONS } from '../constants';
import { useAuth } from '../hooks/AuthProvider';
import { useToast } from '../hooks/useToast';
import { userService } from '../services/userService';
import { roleService } from '../services/roleService';
import { UserForm, type UserFormValues } from '../forms/UserForm';
import { ResetUserPasswordForm } from '../forms/ResetUserPasswordForm';
import { UserStatusBadge } from '../components/StatusBadge';
import type { RoleResponse, UserResponse, UserStatus } from '../types';

const ALL = '__all__';

export function UserManagementPage() {
  const { user: currentUser } = useAuth();
  const toast = useToast();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [roleId, setRoleId] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [editing, setEditing] = useState<UserResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [resetting, setResetting] = useState<UserResponse | null>(null);
  const [deleting, setDeleting] = useState<UserResponse | null>(null);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  // A filter change must return to page 0, or a search matching two rows on
  // page 4 shows an empty table.
  useEffect(() => { setPage(0); }, [debouncedSearch, status, roleId, size]);

  const fetcher = useCallback(
    () => userService.search({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : (status as UserStatus),
      roleId: roleId === ALL ? undefined : Number(roleId),
      page,
      size,
      sortBy: 'fullName',
      sortDir: 'asc',
    }),
    [debouncedSearch, status, roleId, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher,
    [debouncedSearch, status, roleId, page, size]);

  useEffect(() => {
    void (async () => {
      try {
        setRoles(await roleService.list());
      } catch {
        // Roles only populate the filter and the form; a failure here must not
        // block the user list, which is the point of the page.
        setRoles([]);
      }
    })();
  }, []);

  const roleOptions = useMemo(
    () => roles.map((role) => ({ value: String(role.id), label: role.name })),
    [roles],
  );

  const handleCreate = async (values: UserFormValues) => {
    await userService.create({
      fullName: values.fullName,
      mobileNo: values.mobileNo,
      email: values.email ? values.email : null,
      employeeCode: values.employeeCode ? values.employeeCode : null,
      roleId: values.roleId,
      password: values.password,
      mustChangePassword: values.mustChangePassword,
    });
    setCreating(false);
    toast.success('User created.');
    await reload();
  };

  const handleUpdate = async (values: UserFormValues) => {
    if (!editing) return;
    await userService.update(editing.id, {
      fullName: values.fullName,
      mobileNo: values.mobileNo,
      email: values.email ? values.email : null,
      employeeCode: values.employeeCode ? values.employeeCode : null,
      roleId: values.roleId,
      // Present whenever the edit schema ran; the fallback keeps the
      // compiler honest about the shared union.
      status: values.status ?? editing.status,
    });
    setEditing(null);
    toast.success('User updated.');
    await reload();
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await userService.remove(deleting.id);
      toast.success(`${deleting.fullName} has been deactivated.`);
      await reload();
    } catch (caught) {
      // LAST_OWNER_PROTECTED arrives as 422 with a full explanation; showing it
      // verbatim is more useful than a generic failure.
      toast.error(caught, 'Could not deactivate this user.');
      throw caught;
    }
  };

  return (
    <>
      <PageHeader
        title="Users"
        description="Employee accounts. There is no self-registration: every account is created here."
        actions={
          <PermissionGate permission={PERMISSIONS.USER_MANAGE}>
            <Button onClick={() => setCreating(true)}>
              <UserPlus className="h-4 w-4" />
              <span className="hidden sm:inline">Add user</span>
            </Button>
          </PermissionGate>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput value={search} onChange={setSearch}
                     placeholder="Name, mobile, email or code…" />

        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-40"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {USER_STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={roleId} onValueChange={setRoleId}>
          <SelectTrigger className="sm:w-44"><SelectValue placeholder="Role" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All roles</SelectItem>
            {roleOptions.map((option) => (
              <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead className="hidden sm:table-cell">Mobile</TableHead>
                  <TableHead className="hidden lg:table-cell">Role</TableHead>
                  <TableHead className="hidden xl:table-cell">Last sign-in</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>

              {loading ? (
                <TableSkeleton columns={6} rows={size > 10 ? 8 : 5} />
              ) : (
                <TableBody>
                  {data?.content.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell>
                        <span className="font-medium">{row.fullName}</span>
                        <span className="mt-0.5 block text-xs text-muted-foreground sm:hidden">
                          {row.mobileNo}
                        </span>
                        <span className="mt-0.5 hidden text-xs text-muted-foreground lg:block">
                          {row.email ?? row.employeeCode ?? '—'}
                        </span>
                      </TableCell>
                      <TableCell className="tabular hidden sm:table-cell">{row.mobileNo}</TableCell>
                      <TableCell className="hidden lg:table-cell">{row.roleName}</TableCell>
                      <TableCell className="tabular hidden xl:table-cell text-sm text-muted-foreground">
                        {formatDateTime(row.lastLoginAt)}
                      </TableCell>
                      <TableCell><UserStatusBadge status={row.status} /></TableCell>
                      <TableCell>
                        <PermissionGate permission={PERMISSIONS.USER_MANAGE}>
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button variant="ghost" size="icon" className="h-8 w-8"
                                      aria-label={`Actions for ${row.fullName}`}>
                                <MoreHorizontal className="h-4 w-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              <DropdownMenuItem onClick={() => setEditing(row)}>
                                <Pencil className="h-4 w-4" />
                                Edit
                              </DropdownMenuItem>
                              <DropdownMenuItem onClick={() => setResetting(row)}>
                                <KeyRound className="h-4 w-4" />
                                Reset password
                              </DropdownMenuItem>
                              <DropdownMenuItem
                                destructive
                                disabled={row.id === currentUser?.id}
                                onClick={() => setDeleting(row)}
                              >
                                <Trash2 className="h-4 w-4" />
                                Deactivate
                              </DropdownMenuItem>
                            </DropdownMenuContent>
                          </DropdownMenu>
                        </PermissionGate>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              )}
            </Table>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState
                icon={Users}
                title="No users match these filters"
                description="Try clearing the search box or the status filter."
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>

      <Dialog open={creating} onOpenChange={(open) => !open && setCreating(false)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Add user</DialogTitle>
            <DialogDescription>
              Give the employee the temporary password in person. They will be asked to
              replace it at first sign-in.
            </DialogDescription>
          </DialogHeader>
          <UserForm roles={roles} onSubmit={handleCreate} onCancel={() => setCreating(false)} />
        </DialogContent>
      </Dialog>

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Edit user</DialogTitle>
            <DialogDescription>{editing?.fullName}</DialogDescription>
          </DialogHeader>
          {editing ? (
            <UserForm user={editing} roles={roles} onSubmit={handleUpdate}
                      onCancel={() => setEditing(null)} />
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog open={resetting !== null} onOpenChange={(open) => !open && setResetting(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Reset password</DialogTitle>
          </DialogHeader>
          {resetting ? (
            <ResetUserPasswordForm
              userName={resetting.fullName}
              onCancel={() => setResetting(null)}
              onSubmit={async (values) => {
                await userService.resetPassword(resetting.id, values);
                setResetting(null);
                toast.success('Password reset. The user must change it at next sign-in.');
              }}
            />
          ) : null}
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Deactivate this user?"
        description={`${deleting?.fullName ?? 'This user'} will be signed out everywhere and will no longer be able to sign in. Their name stays on past records, so history is preserved.`}
        confirmLabel="Deactivate"
        destructive
        onConfirm={handleDelete}
      />
    </>
  );
}
