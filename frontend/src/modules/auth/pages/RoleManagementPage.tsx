import { useCallback, useEffect, useState } from 'react';
import { Loader2, MoreHorizontal, Pencil, Plus, ShieldCheck, Trash2 } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/shared/components/ui/dropdown-menu';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { ApiError } from '@/shared/types/api';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '../constants';
import { useToast } from '../hooks/useToast';
import { roleService } from '../services/roleService';
import { permissionService } from '../services/permissionService';
import { RoleForm } from '../forms/RoleForm';
import { RoleStatusBadge } from '../components/StatusBadge';
import type { PermissionGroupResponse, RoleResponse } from '../types';
import type { RoleValues } from '../validation/schemas';

export function RoleManagementPage() {
  const toast = useToast();
  const [roles, setRoles] = useState<RoleResponse[] | null>(null);
  const [groups, setGroups] = useState<PermissionGroupResponse[]>([]);
  const [error, setError] = useState<ApiError | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<RoleResponse | null>(null);
  const [deleting, setDeleting] = useState<RoleResponse | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [roleList, permissionGroups] = await Promise.all([
        roleService.list(),
        permissionService.grouped(),
      ]);
      setRoles(roleList);
      setGroups(permissionGroups);
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Could not load roles', code: 'INTERNAL_ERROR', status: 500 }));
      setRoles([]);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const toRequest = (values: RoleValues) => ({
    code: values.code,
    name: values.name,
    description: values.description ? values.description : null,
    permissions: values.permissions,
    status: values.status,
  });

  const handleCreate = async (values: RoleValues) => {
    await roleService.create(toRequest(values));
    setCreating(false);
    toast.success('Role created.');
    await load();
  };

  const handleUpdate = async (values: RoleValues) => {
    if (!editing) return;
    await roleService.update(editing.id, toRequest(values));
    setEditing(null);
    toast.success('Role updated. Anyone holding it has been signed out.');
    await load();
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await roleService.remove(deleting.id);
      toast.success('Role deleted.');
      await load();
    } catch (caught) {
      // System roles and roles still in use are refused with 422 and an
      // explanation that names the blocker; show it as-is.
      toast.error(caught, 'Could not delete this role.');
      throw caught;
    }
  };

  return (
    <>
      <PageHeader
        title="Roles"
        description="A role is a set of permissions. Authorisation is permission-based, never based on the role name."
        actions={
          <PermissionGate permission={PERMISSIONS.ROLE_MANAGE}>
            <Button onClick={() => setCreating(true)}>
              <Plus className="h-4 w-4" />
              <span className="hidden sm:inline">Add role</span>
            </Button>
          </PermissionGate>
        }
      />

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={load} />
        ) : roles === null ? (
          <div className="flex justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
          </div>
        ) : roles.length === 0 ? (
          <EmptyState icon={ShieldCheck} title="No roles found" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Role</TableHead>
                <TableHead className="hidden lg:table-cell">Permissions</TableHead>
                <TableHead className="hidden sm:table-cell">Users</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-12" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {roles.map((role) => (
                <TableRow key={role.id}>
                  <TableCell>
                    <span className="flex flex-wrap items-center gap-2 font-medium">
                      {role.name}
                      {role.systemRole ? <Badge variant="secondary">System</Badge> : null}
                    </span>
                    <span className="mt-0.5 block font-mono text-xs text-muted-foreground">
                      {role.code}
                    </span>
                    {role.description ? (
                      <span className="mt-0.5 block text-xs text-muted-foreground lg:hidden">
                        {role.description}
                      </span>
                    ) : null}
                  </TableCell>
                  <TableCell className="hidden lg:table-cell">
                    <Badge variant="outline">{role.permissions.length} permissions</Badge>
                  </TableCell>
                  <TableCell className="tabular hidden sm:table-cell">{role.userCount}</TableCell>
                  <TableCell><RoleStatusBadge status={role.status} /></TableCell>
                  <TableCell>
                    <PermissionGate permission={PERMISSIONS.ROLE_MANAGE}>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8"
                                  aria-label={`Actions for ${role.name}`}>
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => setEditing(role)}>
                            <Pencil className="h-4 w-4" />
                            Edit permissions
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            destructive
                            disabled={role.systemRole || role.userCount > 0}
                            onClick={() => setDeleting(role)}
                          >
                            <Trash2 className="h-4 w-4" />
                            Delete
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </PermissionGate>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <Dialog open={creating} onOpenChange={(open) => !open && setCreating(false)}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>Add role</DialogTitle>
            <DialogDescription>
              Pick only the permissions this job actually needs.
            </DialogDescription>
          </DialogHeader>
          <RoleForm permissionGroups={groups} onSubmit={handleCreate}
                    onCancel={() => setCreating(false)} />
        </DialogContent>
      </Dialog>

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>Edit role</DialogTitle>
            <DialogDescription>
              {editing?.name} · {editing?.userCount ?? 0} user(s)
            </DialogDescription>
          </DialogHeader>
          {editing ? (
            <RoleForm role={editing} permissionGroups={groups} onSubmit={handleUpdate}
                      onCancel={() => setEditing(null)} />
          ) : null}
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete this role?"
        description={`${deleting?.name ?? 'This role'} will be removed permanently. This is only possible because no user currently holds it.`}
        confirmLabel="Delete"
        destructive
        onConfirm={handleDelete}
      />
    </>
  );
}
