import * as React from 'react';
import { cn } from '@/shared/lib/utils';

/**
 * CR-061. Below the `sm` breakpoint every table re-flows into a stacked list
 * (the `[data-table-cards]` rules in index.css). A seven-column table on a
 * 360px screen was a horizontal scrollbar with three visible columns, which
 * is most of what made the phone build read as a shrunken desktop page.
 *
 * The stacked form has no header row, so each value has to carry its own
 * label. Asking all 39 call sites to repeat their column headings on every
 * cell would have drifted the first time a heading was reworded, so the
 * headers register themselves here by column index and the cells read theirs
 * back out. One primitive change reaches every existing and future table;
 * `label` on a cell is the escape hatch when the derived text is wrong.
 *
 * `sm` (640px) is the cut-over deliberately: the tables already mark their
 * optional columns `hidden sm:table-cell` / `hidden md:table-cell`, so a
 * column the page had already decided a phone should not see stays hidden in
 * the stacked list too, instead of reappearing as a row of noise.
 */
interface TableLabelsValue {
  labels: string[];
  register: (index: number, label: string) => void;
}

const TableLabelsContext = React.createContext<TableLabelsValue | null>(null);
/** A cell's position in its row, supplied by TableRow. */
const ColumnIndexContext = React.createContext<number | null>(null);

export interface TableProps extends React.HTMLAttributes<HTMLTableElement> {
  /** Set false for a table whose value is the grid itself (a matrix, a wide report) and which should keep scrolling horizontally instead. */
  mobileCards?: boolean;
}

const Table = React.forwardRef<HTMLTableElement, TableProps>(
  ({ className, mobileCards = true, ...props }, ref) => {
    const [labels, setLabels] = React.useState<string[]>([]);

    const register = React.useCallback((index: number, label: string) => {
      setLabels((current) => {
        if (current[index] === label) return current;
        const next = current.slice();
        next[index] = label;
        return next;
      });
    }, []);

    const value = React.useMemo<TableLabelsValue>(() => ({ labels, register }), [labels, register]);

    return (
      <TableLabelsContext.Provider value={value}>
        {/* Wrapper scrolls horizontally from sm up; below that the stacked
            list needs no scroller and an overflow context here would clip
            the sticky pagination bar underneath it. */}
        <div className="relative w-full sm:overflow-x-auto" data-table-cards={mobileCards ? 'true' : undefined}>
          <table ref={ref} className={cn('w-full caption-bottom text-sm', className)} {...props} />
        </div>
      </TableLabelsContext.Provider>
    );
  },
);
Table.displayName = 'Table';

const TableHeader = React.forwardRef<HTMLTableSectionElement, React.HTMLAttributes<HTMLTableSectionElement>>(
  ({ className, ...props }, ref) => <thead ref={ref} className={cn('[&_tr]:border-b', className)} {...props} />,
);
TableHeader.displayName = 'TableHeader';

const TableBody = React.forwardRef<HTMLTableSectionElement, React.HTMLAttributes<HTMLTableSectionElement>>(
  ({ className, ...props }, ref) => (
    <tbody ref={ref} className={cn('[&_tr:last-child]:border-0', className)} {...props} />
  ),
);
TableBody.displayName = 'TableBody';

const TableRow = React.forwardRef<HTMLTableRowElement, React.HTMLAttributes<HTMLTableRowElement>>(
  ({ className, children, ...props }, ref) => (
    <tr
      ref={ref}
      className={cn('border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted', className)}
      {...props}
    >
      {/*
        A context provider renders no DOM of its own, so the <td>/<th> stay
        direct children of the <tr> and the table markup is unchanged.
        React.Children.map flattens nested arrays and still visits null
        children, so a column rendered conditionally keeps the same index in
        the header row and the body rows.
      */}
      {React.Children.map(children, (child, index) =>
        React.isValidElement(child)
          ? <ColumnIndexContext.Provider value={index}>{child}</ColumnIndexContext.Provider>
          : child,
      )}
    </tr>
  ),
);
TableRow.displayName = 'TableRow';

const TableHead = React.forwardRef<HTMLTableCellElement, React.ThHTMLAttributes<HTMLTableCellElement>>(
  ({ className, ...props }, ref) => {
    const context = React.useContext(TableLabelsContext);
    const index = React.useContext(ColumnIndexContext);
    const innerRef = React.useRef<HTMLTableCellElement | null>(null);

    // No dependency array on purpose: the heading is read from rendered text,
    // which can change without any prop this component can watch. `register`
    // bails when the text is unchanged, so this settles after one pass.
    React.useEffect(() => {
      if (!context || index === null) return;
      context.register(index, innerRef.current?.textContent?.trim() ?? '');
    });

    return (
      <th
        ref={(node) => {
          innerRef.current = node;
          if (typeof ref === 'function') ref(node);
          else if (ref) ref.current = node;
        }}
        className={cn(
          'h-11 whitespace-nowrap px-3 text-left align-middle text-xs font-semibold uppercase tracking-wide text-muted-foreground',
          className,
        )}
        {...props}
      />
    );
  },
);
TableHead.displayName = 'TableHead';

export interface TableCellProps extends React.TdHTMLAttributes<HTMLTableCellElement> {
  /** Overrides the column heading used as this cell's label in the stacked mobile list. Pass an empty string to suppress the label entirely. */
  label?: string;
}

const TableCell = React.forwardRef<HTMLTableCellElement, TableCellProps>(
  ({ className, label, colSpan, ...props }, ref) => {
    const context = React.useContext(TableLabelsContext);
    const index = React.useContext(ColumnIndexContext);

    const derived = label ?? (index === null ? undefined : context?.labels[index]);
    // A spanning cell (an inline "no results" row) belongs to no single
    // column, so labelling it with the first heading would be a lie.
    const showLabel = colSpan === undefined && derived;

    return (
      <td
        ref={ref}
        colSpan={colSpan}
        data-label={showLabel || undefined}
        data-column-index={index ?? undefined}
        className={cn('px-3 py-2.5 align-middle', className)}
        {...props}
      />
    );
  },
);
TableCell.displayName = 'TableCell';

export { Table, TableHeader, TableBody, TableRow, TableHead, TableCell };
