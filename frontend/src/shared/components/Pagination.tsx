import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/shared/components/ui/select';
import { PAGE_SIZE_OPTIONS } from '@/shared/constants';
import type { PageResponse } from '@/shared/types/api';

interface PaginationProps {
  page: PageResponse<unknown>;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
}

export function Pagination({ page, onPageChange, onSizeChange }: PaginationProps) {
  const from = page.totalElements === 0 ? 0 : page.page * page.size + 1;
  const to = Math.min((page.page + 1) * page.size, page.totalElements);

  return (
    <div className="flex flex-col-reverse items-center gap-3 border-t px-3 py-3 sm:flex-row sm:justify-between">
      <p className="tabular text-sm text-muted-foreground">
        {from}–{to} of {page.totalElements}
      </p>

      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2">
          <span className="hidden text-sm text-muted-foreground sm:inline">Rows</span>
          <Select value={String(page.size)} onValueChange={(value) => onSizeChange(Number(value))}>
            <SelectTrigger className="h-9 w-[4.5rem]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PAGE_SIZE_OPTIONS.map((size) => (
                <SelectItem key={size} value={String(size)}>{size}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex items-center gap-1">
          <Button
            variant="outline" size="icon" className="h-9 w-9"
            onClick={() => onPageChange(page.page - 1)}
            disabled={page.first}
            aria-label="Previous page"
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="tabular px-2 text-sm">
            {page.page + 1} / {Math.max(page.totalPages, 1)}
          </span>
          <Button
            variant="outline" size="icon" className="h-9 w-9"
            onClick={() => onPageChange(page.page + 1)}
            disabled={page.last}
            aria-label="Next page"
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}
