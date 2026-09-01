import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { cn } from '@/shared/lib/utils';

interface BackLinkProps {
  to: string;
  label: string;
  className?: string;
}

/**
 * A real, visible button - not muted text easy to mistake for a caption,
 * and not one more entry crammed into a detail page's right-aligned
 * actions row next to Preview/Download/Edit, where it read as just
 * another action rather than "the way back". Always the same shape, in
 * the same top-left position, above PageHeader, across every detail
 * page - replacing two inconsistent prior patterns (muted text on most
 * pages, a same-styled Button buried in the actions row on Invoice/
 * Quotation).
 */
export function BackLink({ to, label, className }: BackLinkProps) {
  return (
    <Link
      to={to}
      className={cn(
        'mb-2 inline-flex items-center gap-1.5 rounded-md border bg-background px-3 py-1.5',
        'text-sm font-medium text-foreground shadow-sm transition-colors hover:bg-accent hover:text-accent-foreground',
        className,
      )}
    >
      <ArrowLeft className="h-4 w-4" aria-hidden />
      {label}
    </Link>
  );
}
