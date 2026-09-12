import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  description?: string;
  actions?: ReactNode;
}

/*
  BUG-FE-035: `sm:flex-wrap` on the row is what protects the title. Every
  caller passes its actions already wrapped in a div of its own, so the
  container below holds exactly ONE flex item whose min-content width is the
  whole toolbar - Invoice detail carries eight actions measuring 1039px.
  Without wrapping at this level the toolbar simply took the space: at 1440px
  the title column was squeezed to 69px ("INV-000027" rendered as "I...") and
  the description wrapped one word per line; at 768px it reached 0px and the
  toolbar ran off the page. Wrapping drops the toolbar onto its own row
  instead, which costs nothing on a wide screen where both still fit.
*/
export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-start sm:justify-between">
      {/*
        `sm:basis-64` is the floor that makes the wrap happen. With a content
        basis the title would still be the smaller item and lose the space
        race; 16rem is wide enough for a document number plus its customer
        line, and `flex-1` lets it take the whole row once the toolbar leaves.
      */}
      <div className="min-w-0 sm:flex-1 sm:basis-64">
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
