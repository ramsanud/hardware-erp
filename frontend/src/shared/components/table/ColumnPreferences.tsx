import { useCallback, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { ArrowDown, ArrowUp, Columns3, RotateCcw } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Checkbox } from '@/shared/components/ui/checkbox';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/shared/components/ui/dialog';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { readScoped, writeScoped } from '@/theme/themeScope';
import { cn } from '@/shared/lib/utils';

/**
 * CR-068. Per-user, per-list column choice: which columns a list shows and in
 * what order.
 *
 * Why the preference is per user and not one setting per shop: two people at
 * the same counter want different lists. A billing clerk scans a barcode; the
 * owner reads prices; whoever files GST wants the HSN code on screen. One
 * shop-wide column set would make each of them wrong most of the time.
 *
 * Storage is client-side, scoped by user id through the same helper the theme
 * uses (`themeScope`). That scope exists precisely so one browser shared by two
 * shops cannot leak one shop's preference into the other's session - login
 * identifiers are globally unique across tenants (CR-016), so a user id is
 * already an unambiguous per-tenant key. A column layout is presentation, not
 * shop data: nothing is lost if it does not follow the user to a new device,
 * and putting it server-side would mean a table, a migration and an endpoint
 * for a setting with no business consequence.
 */
export interface ColumnDef<TRow> {
  /**
   * Stable key. This value is PERSISTED, so it falls under the naming law:
   * rename it and every user who had ordered that column silently loses the
   * choice. `header` is presentation and may be reworded freely.
   */
  id: string;
  header: string;
  cell: (row: TRow) => ReactNode;
  /** False makes the column opt-in: offered in the picker, off until chosen. */
  defaultVisible?: boolean;
  /**
   * Pinned first and never hideable. The identity column - the phone card
   * reads `td[data-column-index="0"]` as the item's heading, so something has
   * to be guaranteed to sit there.
   */
  locked?: boolean;
  /** Applied only while the column sits at its default - see `resolveClassName`. */
  headClassName?: string;
  cellClassName?: string;
  /** Offered only to a user holding this permission. */
  permission?: string;
}

interface StoredPreference {
  order: string[];
  hidden: string[];
}

const STORAGE_PREFIX = 'hardware-erp-columns';

function read(listId: string): StoredPreference | null {
  const raw = readScoped(`${STORAGE_PREFIX}:${listId}`);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<StoredPreference>;
    if (!Array.isArray(parsed.order) || !Array.isArray(parsed.hidden)) return null;
    return {
      order: parsed.order.filter((value) => typeof value === 'string'),
      hidden: parsed.hidden.filter((value) => typeof value === 'string'),
    };
  } catch {
    // A half-written or hand-edited value must not take the whole list down.
    return null;
  }
}

export interface ColumnPreferences<TRow> {
  /** Ordered, permission-filtered, visibility-filtered: what the table renders. */
  visible: ColumnDef<TRow>[];
  /** Everything the picker may offer, in the user's current order. */
  ordered: ColumnDef<TRow>[];
  hidden: Set<string>;
  customised: boolean;
  toggle: (id: string, next: boolean) => void;
  move: (id: string, direction: -1 | 1) => void;
  reset: () => void;
  /**
   * A column the user switched on themselves shows at every width, phone card
   * included; a column still at its default keeps the page's own responsive
   * rules. Turning a column on and then not finding it on the device in your
   * hand would read as the setting being broken.
   */
  resolveClassName: (column: ColumnDef<TRow>, kind: 'head' | 'cell') => string | undefined;
}

export function useColumnPreferences<TRow>(
  listId: string,
  catalogue: ColumnDef<TRow>[],
): ColumnPreferences<TRow> {
  const { hasPermission } = useAuth();
  const [preference, setPreference] = useState<StoredPreference | null>(() => read(listId));

  const allowed = useMemo(
    () => catalogue.filter((column) => !column.permission || hasPermission(column.permission)),
    [catalogue, hasPermission],
  );

  const persist = useCallback((next: StoredPreference | null) => {
    setPreference(next);
    writeScoped(`${STORAGE_PREFIX}:${listId}`, next ? JSON.stringify(next) : '');
  }, [listId]);

  const ordered = useMemo(() => {
    const locked = allowed.filter((column) => column.locked);
    const movable = allowed.filter((column) => !column.locked);
    if (!preference?.order.length) return [...locked, ...movable];
    const byId = new Map(movable.map((column) => [column.id, column]));
    // Stored ids first, in the stored order; any column the catalogue has
    // gained since the preference was written appends rather than vanishing.
    const chosen = preference.order
      .map((id) => byId.get(id))
      .filter((column): column is ColumnDef<TRow> => Boolean(column));
    const rest = movable.filter((column) => !preference.order.includes(column.id));
    return [...locked, ...chosen, ...rest];
  }, [allowed, preference]);

  const hidden = useMemo(() => {
    if (preference) return new Set(preference.hidden);
    return new Set(
      allowed.filter((column) => column.defaultVisible === false).map((column) => column.id),
    );
  }, [allowed, preference]);

  const visible = useMemo(
    () => ordered.filter((column) => column.locked || !hidden.has(column.id)),
    [ordered, hidden],
  );

  const snapshot = useCallback((): StoredPreference => ({
    order: ordered.filter((column) => !column.locked).map((column) => column.id),
    hidden: [...hidden],
  }), [ordered, hidden]);

  const toggle = useCallback((id: string, next: boolean) => {
    const current = snapshot();
    const nextHidden = new Set(current.hidden);
    if (next) nextHidden.delete(id);
    else nextHidden.add(id);
    persist({ order: current.order, hidden: [...nextHidden] });
  }, [persist, snapshot]);

  const move = useCallback((id: string, direction: -1 | 1) => {
    const current = snapshot();
    const from = current.order.indexOf(id);
    const to = from + direction;
    if (from < 0 || to < 0 || to >= current.order.length) return;
    const order = current.order.slice();
    [order[from], order[to]] = [order[to], order[from]];
    persist({ order, hidden: current.hidden });
  }, [persist, snapshot]);

  const reset = useCallback(() => persist(null), [persist]);

  const resolveClassName = useCallback(
    (column: ColumnDef<TRow>, kind: 'head' | 'cell') => {
      const userAdded = column.defaultVisible === false && !hidden.has(column.id);
      // `show-on-card` keeps a user-added column on the phone list, where a
      // page's own `hidden md:table-cell` would otherwise drop it (BUG-FE-026).
      if (userAdded) return kind === 'cell' ? 'show-on-card' : undefined;
      return kind === 'head' ? column.headClassName : column.cellClassName;
    },
    [hidden],
  );

  return {
    visible, ordered, hidden, customised: preference !== null,
    toggle, move, reset, resolveClassName,
  };
}

interface ColumnSettingsProps<TRow> {
  preferences: ColumnPreferences<TRow>;
  /** Names the list in the dialog copy, e.g. "product". */
  label: string;
}

/**
 * The picker. Changes apply and persist immediately - there is no Save,
 * because the list behind the dialog is the preview, and a Save button would
 * only invite the question of what Cancel then means for a change already on
 * screen. "Reset to default" is the way back.
 *
 * Order is changed with buttons rather than dragging: this has to work on the
 * phone, where the lists are most cramped and HTML5 drag-and-drop does not
 * work at all.
 */
export function ColumnSettings<TRow>({ preferences, label }: ColumnSettingsProps<TRow>) {
  const [open, setOpen] = useState(false);
  const { ordered, hidden, customised, toggle, move, reset } = preferences;
  const locked = ordered.filter((column) => column.locked);
  const movable = ordered.filter((column) => !column.locked);

  return (
    <>
      {/*
        The visible label collapses to the icon on a phone, so the button needs
        a name of its own or it reaches a screen reader as "button". The dot is
        decorative - "customised" is already said in the label.
      */}
      <Button
        variant="outline"
        onClick={() => setOpen(true)}
        aria-label={customised ? 'Columns (customised)' : 'Columns'}
      >
        <Columns3 className="h-4 w-4" />
        <span className="hidden sm:inline">Columns</span>
        {customised ? <span className="h-1.5 w-1.5 rounded-full bg-primary" aria-hidden /> : null}
      </Button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Columns</DialogTitle>
            <DialogDescription>
              Choose what the {label} list shows, and in what order. Saved for you on this device.
            </DialogDescription>
          </DialogHeader>

          <ul className="space-y-0.5">
            {locked.map((column) => (
              <li
                key={column.id}
                className="flex items-center gap-3 rounded-md px-2 py-2 text-sm text-muted-foreground"
              >
                <Checkbox checked disabled aria-label={`${column.header} is always shown`} />
                <span className="flex-1 truncate">{column.header}</span>
                <span className="shrink-0 text-xs">Always shown</span>
              </li>
            ))}

            {movable.map((column, index) => {
              const shown = !hidden.has(column.id);
              return (
                <li
                  key={column.id}
                  className="flex items-center gap-3 rounded-md px-2 py-1.5 text-sm hover:bg-accent/50"
                >
                  <Checkbox
                    id={`column-${column.id}`}
                    checked={shown}
                    onCheckedChange={(value) => toggle(column.id, value === true)}
                  />
                  <label
                    htmlFor={`column-${column.id}`}
                    className={cn('flex-1 cursor-pointer truncate', !shown && 'text-muted-foreground')}
                  >
                    {column.header}
                  </label>
                  <span className="flex shrink-0 items-center">
                    <Button
                      variant="ghost" size="icon" className="h-7 w-7"
                      disabled={index === 0}
                      onClick={() => move(column.id, -1)}
                      aria-label={`Move ${column.header} earlier`}
                    >
                      <ArrowUp className="h-3.5 w-3.5" />
                    </Button>
                    <Button
                      variant="ghost" size="icon" className="h-7 w-7"
                      disabled={index === movable.length - 1}
                      onClick={() => move(column.id, 1)}
                      aria-label={`Move ${column.header} later`}
                    >
                      <ArrowDown className="h-3.5 w-3.5" />
                    </Button>
                  </span>
                </li>
              );
            })}
          </ul>

          <DialogFooter>
            <Button variant="ghost" onClick={reset} disabled={!customised}>
              <RotateCcw className="h-4 w-4" />
              Reset to default
            </Button>
            <Button onClick={() => setOpen(false)}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
