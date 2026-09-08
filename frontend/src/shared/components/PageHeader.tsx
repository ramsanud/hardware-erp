import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
}

export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0">
        <h1 className="truncate text-xl font-semibold tracking-tight sm:text-2xl">{title}</h1>
        {description ? (
          <p className="mt-1 text-sm text-muted-foreground">{description}</p>
        ) : null}
      </div>
      {/*
        BUG-FE-034: flex-wrap AND no shrink-0. The two go together.
        `shrink-0` here is what let a busy toolbar run off the page - Quotation
        detail carries seven actions (Preview, Download PDF, Edit, Repeat, Mark
        sent, Accepted, Rejected) and at 768px they measured 843px wide. A
        flex item that may not shrink never wraps either, however many
        flex-wrap classes it carries: wrapping only begins once the container
        is narrower than its content, and shrink-0 forbids exactly that.
        Letting it shrink costs nothing on a wide screen, where there is enough
        room for one row anyway.
      */}
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}
