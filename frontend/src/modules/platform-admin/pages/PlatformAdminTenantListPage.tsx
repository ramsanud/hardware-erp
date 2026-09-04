import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2 } from 'lucide-react';
import { Badge } from '@/shared/components/ui/badge';
import { Card } from '@/shared/components/ui/card';
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
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from '@/shared/constants';
import { formatDateTime } from '@/shared/lib/utils';
import { PLATFORM_ADMIN_ROUTES } from '../constants';
import { platformAdminTenantService } from '../services/platformAdminTenantService';
import type { SubscriptionTier, TenantStatus } from '../types';

const ALL = '__all__';

const TIER_LABEL: Record<SubscriptionTier, string> = { FREE: 'Free', PRO: 'Pro', MAX: 'Max' };

export function PlatformAdminTenantListPage() {
  const navigate = useNavigate();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<string>(ALL);
  const [tier, setTier] = useState<string>(ALL);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);

  const debouncedSearch = useDebouncedValue(search, SEARCH_DEBOUNCE_MS);

  useEffect(() => { setPage(0); }, [debouncedSearch, status, tier, size]);

  const fetcher = useCallback(
    () => platformAdminTenantService.list({
      search: debouncedSearch || undefined,
      status: status === ALL ? undefined : (status as TenantStatus),
      tier: tier === ALL ? undefined : (tier as SubscriptionTier),
      page,
      size,
    }),
    [debouncedSearch, status, tier, page, size],
  );

  const { data, loading, error, reload } = useAsyncList(fetcher, [debouncedSearch, status, tier, page, size]);

  return (
    <>
      <PageHeader title="Tenants" description="Every shop running on Hardware ERP." />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <SearchInput value={search} onChange={setSearch} placeholder="Name, slug, email or phone…" />
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="sm:w-40"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            <SelectItem value="ACTIVE">Active</SelectItem>
            <SelectItem value="SUSPENDED">Suspended</SelectItem>
          </SelectContent>
        </Select>
        <Select value={tier} onValueChange={setTier}>
          <SelectTrigger className="sm:w-36"><SelectValue placeholder="Plan" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All plans</SelectItem>
            <SelectItem value="FREE">Free</SelectItem>
            <SelectItem value="PRO">Pro</SelectItem>
            <SelectItem value="MAX">Max</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : (
          <>
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Tenant</TableHead>
                    <TableHead className="hidden md:table-cell">Owner</TableHead>
                    <TableHead className="hidden lg:table-cell">Contact</TableHead>
                    <TableHead>Plan</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="hidden sm:table-cell">Users</TableHead>
                    <TableHead className="hidden lg:table-cell">Created</TableHead>
                    <TableHead className="hidden xl:table-cell">Last active</TableHead>
                  </TableRow>
                </TableHeader>

                {loading ? (
                  <TableSkeleton columns={8} rows={size > 10 ? 8 : 5} />
                ) : (
                  <TableBody>
                    {data?.content.map((row) => (
                      <TableRow
                        key={row.id}
                        className="cursor-pointer"
                        onClick={() => navigate(PLATFORM_ADMIN_ROUTES.tenantDetail(row.id))}
                      >
                        <TableCell>
                          <span className="font-medium">{row.name}</span>
                          <span className="mt-0.5 block text-xs text-muted-foreground">{row.slug}</span>
                        </TableCell>
                        <TableCell className="hidden md:table-cell">
                          {row.ownerName ?? <span className="text-muted-foreground">No active owner</span>}
                        </TableCell>
                        <TableCell className="hidden lg:table-cell text-xs text-muted-foreground">
                          {row.email ?? row.phone ?? '—'}
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">{TIER_LABEL[row.subscriptionTier]}</Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant={row.status === 'ACTIVE' ? 'default' : 'destructive'}>
                            {row.status === 'ACTIVE' ? 'Active' : 'Suspended'}
                          </Badge>
                        </TableCell>
                        <TableCell className="tabular hidden sm:table-cell">{row.userCount}</TableCell>
                        <TableCell className="hidden lg:table-cell text-xs text-muted-foreground">
                          {formatDateTime(row.createdAt)}
                        </TableCell>
                        <TableCell className="hidden xl:table-cell text-xs text-muted-foreground">
                          {row.lastActiveAt ? formatDateTime(row.lastActiveAt) : 'Never'}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                )}
              </Table>
            </div>

            {!loading && data && data.content.length === 0 ? (
              <EmptyState
                icon={Building2}
                title="No tenants match these filters"
                description="Try clearing the search box, status or plan filter."
              />
            ) : null}

            {data && data.content.length > 0 ? (
              <Pagination page={data} onPageChange={setPage} onSizeChange={setSize} />
            ) : null}
          </>
        )}
      </Card>
    </>
  );
}
