import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Layers, MoreHorizontal, Pencil, Trash2 } from 'lucide-react';
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
import { categoryService } from '../services/categoryService';
import { CategoryForm } from '../forms/CategoryForm';
import { CategoryStatusBadge } from '../components/ProductStatusBadge';
import type { CategoryResponse } from '../types';
import type { CategoryValues } from '../validation/schemas';

/** Not paginated server-side - findAll() returns the whole tree, wrapped to fit useAsyncList's Page shape. */
async function fetchAllAsPage(): Promise<PageResponse<CategoryResponse>> {
  const content = await categoryService.list();
  return { content, page: 0, size: content.length, totalElements: content.length, totalPages: 1, first: true, last: true };
}

export function CategoryListPage() {
  const toast = useToast();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<CategoryResponse | null>(null);
  const [deleting, setDeleting] = useState<CategoryResponse | null>(null);

  const { data, loading, error, reload } = useAsyncList(fetchAllAsPage, []);
  const allCategories = data?.content ?? [];

  // Client-side filter: the category list is a shop's own vocabulary, tens
  // of rows not thousands, so it is already fully loaded here. A server
  // round-trip per keystroke would be slower and no more correct.
  const [filter, setFilter] = useState('');
  const categories = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    if (!needle) return allCategories;
    return allCategories.filter((c) =>
      c.categoryName.toLowerCase().includes(needle)
      || c.categoryCode.toLowerCase().includes(needle)
      || (c.parentCategoryName ?? "").toLowerCase().includes(needle));
  }, [allCategories, filter]);

  const handleCreate = async (values: CategoryValues) => {
    await categoryService.create({
      categoryCode: values.categoryCode || undefined,
      categoryName: values.categoryName,
      parentCategoryId: values.parentCategoryId ?? null,
      description: values.description || null,
      status: values.status,
    });
    setCreating(false);
    toast.success('Category created.');
    await reload();
  };

  const handleUpdate = async (values: CategoryValues) => {
    if (!editing) return;
    await categoryService.update(editing.id, {
      categoryCode: values.categoryCode || undefined,
      categoryName: values.categoryName,
      parentCategoryId: values.parentCategoryId ?? null,
      description: values.description || null,
      status: values.status,
    });
    setEditing(null);
    toast.success('Category updated.');
    await reload();
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await categoryService.remove(deleting.id);
      toast.success(`${deleting.categoryName} has been removed.`);
      await reload();
    } catch (caught) {
      toast.error(caught, 'Could not remove this category.');
      throw caught;
    }
  };

  // A category cannot be its own parent, directly or through a descendant.
  const parentOptionsFor = (self?: CategoryResponse) =>
    allCategories.filter((c) => c.id !== self?.id);

  return (
    <>
      <PageHeader
        title="Categories"
        description="Hierarchical grouping for the product catalogue."
        actions={
          <PermissionGate permission={PERMISSIONS.PRODUCT_MANAGE}>
            <Button onClick={() => setCreating(true)}>Add category</Button>
          </PermissionGate>
        }
      />

      <div className="mb-4">
        <SearchInput value={filter} onChange={setFilter}
                     placeholder="Search categories by name or code..." />
      </div>

      <Card>
        {error ? (
          <ErrorState error={error} onRetry={reload} />
        ) : loading ? (
          <div className="p-6 text-sm text-muted-foreground">Loading…</div>
        ) : categories.length === 0 ? (
          <EmptyState icon={Layers} title="No categories yet"
                      description="Add your first category to start organising products." />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Category</TableHead>
                <TableHead className="hidden sm:table-cell">Parent</TableHead>
                <TableHead className="hidden md:table-cell">Products</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-12" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {categories.map((row) => (
                <TableRow key={row.id}>
                  <TableCell>
                    <span className="font-medium">{row.categoryName}</span>
                    <span className="tabular mt-0.5 block text-xs text-muted-foreground">
                      {row.categoryCode}
                    </span>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">{row.parentCategoryName ?? '—'}</TableCell>
                  <TableCell className="tabular hidden md:table-cell">
                    {row.productCount > 0 ? (
                      <Link to={`${PRODUCT_ROUTES.list}?categoryId=${row.id}`}
                            className="text-primary underline-offset-4 hover:underline"
                            aria-label={`View ${row.productCount} products in ${row.categoryName}`}>
                        {row.productCount}
                      </Link>
                    ) : row.productCount}
                  </TableCell>
                  <TableCell><CategoryStatusBadge status={row.status} /></TableCell>
                  <TableCell>
                    <PermissionGate permission={PERMISSIONS.PRODUCT_MANAGE}>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8"
                                  aria-label={`Actions for ${row.categoryName}`}>
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
        <DialogContent className="sm:max-w-lg">
          <DialogHeader><DialogTitle>Add category</DialogTitle></DialogHeader>
          <CategoryForm categories={parentOptionsFor()} onSubmit={handleCreate}
                       onCancel={() => setCreating(false)} />
        </DialogContent>
      </Dialog>

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader><DialogTitle>Edit category</DialogTitle></DialogHeader>
          {editing ? (
            <CategoryForm category={editing} categories={parentOptionsFor(editing)}
                         onSubmit={handleUpdate} onCancel={() => setEditing(null)} />
          ) : null}
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Delete this category?"
        description={
          deleting && deleting.productCount > 0
            ? `${deleting.productCount} product(s) still use "${deleting.categoryName}". Reassign them first.`
            : `"${deleting?.categoryName ?? 'This category'}" will be permanently removed.`
        }
        confirmLabel="Delete"
        destructive
        onConfirm={handleDelete}
      />
    </>
  );
}
