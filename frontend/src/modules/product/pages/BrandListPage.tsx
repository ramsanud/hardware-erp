import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { MoreHorizontal, Pencil, Tags, Trash2 } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Card } from '@/shared/components/ui/card';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from '@/shared/components/ui/dropdown-menu';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/shared/components/ui/table';
import { PageHeader } from '@/shared/components/PageHeader';
import { PRODUCT_ROUTES } from '../constants';
import { SearchInput } from '@/shared/components/SearchInput';
import { EmptyState } from '@/shared/components/EmptyState';
import { ErrorState } from '@/shared/components/ErrorState';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';
import { useAsyncList } from '@/shared/hooks/useAsyncList';
import type { PageResponse } from '@/shared/types/api';
import { PermissionGate } from '@/routes/RequirePermission';
import { PERMISSIONS } from '@/modules/auth/constants';
import { useToast } from '@/modules/auth/hooks/useToast';
import { brandService } from '../services/brandService';
import { BrandForm } from '../forms/BrandForm';
import { BrandStatusBadge } from '../components/ProductStatusBadge';
import type { BrandResponse } from '../types';
import type { BrandValues } from '../validation/schemas';

async function fetchAllAsPage(): Promise<PageResponse<BrandResponse>> {
  const content = await brandService.list();
  return { content, page: 0, size: content.length, totalElements: content.length, totalPages: 1, first: true, last: true };
}

export function BrandListPage() {
  const toast = useToast();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<BrandResponse | null>(null);
  const [deleting, setDeleting] = useState<BrandResponse | null>(null);

  const { data, loading, error, reload } = useAsyncList(fetchAllAsPage, []);
  const allBrands = data?.content ?? [];

  // Same reasoning as CategoryListPage: a fully-loaded, shop-sized list.
  const [filter, setFilter] = useState('');
  const brands = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    if (!needle) return allBrands;
    return allBrands.filter((b) =>
      b.brandName.toLowerCase().includes(needle)
      || b.brandCode.toLowerCase().includes(needle));
  }, [allBrands, filter]);

  const handleCreate = async (values: BrandValues) => {
    await brandService.create({
      brandCode: values.brandCode || undefined,
      brandName: values.brandName,
      description: values.description || null,
      status: values.status,
    });
    setCreating(false);
    toast.success('Brand created.');
    await reload();
  };

  const handleUpdate = async (values: BrandValues) => {
    if (!editing) return;
    await brandService.update(editing.id, {
      brandCode: values.brandCode || undefined,
      brandName: values.brandName,
      description: values.description || null,
      status: values.status,
    });
    setEditing(null);
    toast.success('Brand updated.');
    await reload();
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await brandService.remove(deleting.id);
      toast.success(`${deleting.brandName} has been removed.`);
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not remove this brand.');
      throw caught;
    }
  };

  return (
    <>
      <PageHeader
        title="Brands"
        description="Brand master for the product catalogue."
        actions={
          <PermissionGate permission={PERMISSIONS.PRODUCT_MANAGE}>
            <Button onClick={() => setCreating(true)}>Add brand</Button>
          </PermissionGate>
        }
      />

      <div className="mb-4">
        <SearchInput value={filter} onChange={setFilter}
                     placeholder="Search brands by name or code..." />
      </div>

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : loading ? (
          <div className="p-6 text-sm text-muted-foreground">Loading…</div>
        ) : brands.length === 0 ? (
          <EmptyState icon={Tags} title="No brands yet"
                      description="Add your first brand to start organising products." />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Brand</TableHead>
                <TableHead className="hidden md:table-cell">Products</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-12" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {brands.map((row) => (
                <TableRow key={row.id}>
                  <TableCell>
                    <span className="font-medium">{row.brandName}</span>
                    <span className="tabular mt-0.5 block text-xs text-muted-foreground">
                      {row.brandCode}
                    </span>
                  </TableCell>
                  <TableCell className="tabular hidden md:table-cell">
                    {row.productCount > 0 ? (
                      <Link to={`${PRODUCT_ROUTES.list}?brandId=${row.id}`}
                            className="text-primary underline-offset-4 hover:underline"
                            aria-label={`View ${row.productCount} products for ${row.brandName}`}>
                        {row.productCount}
                      </Link>
                    ) : row.productCount}
                  </TableCell>
                  <TableCell><BrandStatusBadge status={row.status} /></TableCell>
                  <TableCell>
                    <PermissionGate permission={PERMISSIONS.PRODUCT_MANAGE}>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8"
                                  aria-label={`Actions for ${row.brandName}`}>
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => setEditing(row)}>
                            <Pencil className="h-4 w-4" />
                            Edit
                          </DropdownMenuItem>
                          <DropdownMenuItem destructive onClick={() => setDeleting(row)}>
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
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>Add brand</DialogTitle></DialogHeader>
          <BrandForm onSubmit={handleCreate} onCancel={() => setCreating(false)} />
        </DialogContent>
      </Dialog>

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader><DialogTitle>Edit brand</DialogTitle></DialogHeader>
          {editing ? (
            <BrandForm brand={editing} onSubmit={handleUpdate} onCancel={() => setEditing(null)} />
          ) : null}
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete this brand?"
        description={
          deleting && deleting.productCount > 0
            ? `${deleting.productCount} product(s) still use "${deleting.brandName}". Reassign them first.`
            : `"${deleting?.brandName ?? 'This brand'}" will be permanently removed.`
        }
        confirmLabel="Delete"
        destructive
        onConfirm={handleDelete}
      />
    </>
  );
}
