import { useEffect, useMemo, useState } from 'react';
import { KeyRound, Loader2 } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/shared/components/ui/card';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { SearchInput } from '@/shared/components/SearchInput';
import { ApiError } from '@/shared/types/api';
import { permissionService } from '../services/permissionService';
import type { PermissionGroupResponse } from '../types';

/**
 * Read-only catalogue. Permissions are inserted by each module's Flyway
 * migration, so this page needs no change when Modules 2-12 ship.
 */
export function PermissionViewPage() {
  const [groups, setGroups] = useState<PermissionGroupResponse[] | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [search, setSearch] = useState('');

  const load = async () => {
    setError(null);
    try {
      setGroups(await permissionService.grouped());
    } catch (caught) {
      setError(caught instanceof ApiError
        ? caught
        : new ApiError({ message: 'Could not load permissions', code: 'INTERNAL_ERROR', status: 500 }));
    }
  };

  useEffect(() => { void load(); }, []);

  const filtered = useMemo(() => {
    if (!groups) return [];
    const term = search.trim().toLowerCase();
    if (!term) return groups;
    return groups
      .map((group) => ({
        ...group,
        permissions: group.permissions.filter((permission) =>
          permission.code.toLowerCase().includes(term) ||
          permission.name.toLowerCase().includes(term) ||
          (permission.description ?? '').toLowerCase().includes(term)),
      }))
      .filter((group) => group.permissions.length > 0);
  }, [groups, search]);

  const total = groups?.reduce((sum, group) => sum + group.permissions.length, 0) ?? 0;

  return (
    <>
      <PageHeader
        title="Permissions"
        description={`${total} permissions across ${groups?.length ?? 0} modules. Assign them to roles on the Roles page.`}
      />

      <SearchInput value={search} onChange={setSearch} placeholder="Search permissions…" />

      {error ? (
        <Card><ErrorState error={error} onRetry={load} /></Card>
      ) : groups === null ? (
        <div className="flex justify-center py-16">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-label="Loading" />
        </div>
      ) : filtered.length === 0 ? (
        <Card>
          <EmptyState icon={KeyRound} title="No permissions match your search" />
        </Card>
      ) : (
        <div className="grid gap-4 xl:grid-cols-2">
          {filtered.map((group) => (
            <Card key={group.moduleCode}>
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  {group.moduleCode}
                  <Badge variant="secondary">{group.permissions.length}</Badge>
                </CardTitle>
                <CardDescription>Module {group.moduleCode.toLowerCase()}</CardDescription>
              </CardHeader>
              <CardContent className="px-0 pb-0">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="pl-6">Code</TableHead>
                      <TableHead>Description</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {group.permissions.map((permission) => (
                      <TableRow key={permission.id}>
                        <TableCell className="pl-6 align-top">
                          <span className="font-mono text-xs">{permission.code}</span>
                          <span className="mt-0.5 block text-xs text-muted-foreground">
                            {permission.name}
                          </span>
                        </TableCell>
                        <TableCell className="align-top text-sm text-muted-foreground">
                          {permission.description ?? '—'}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </>
  );
}
