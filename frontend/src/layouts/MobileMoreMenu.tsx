import { useNavigate } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { Sheet, SheetContent, SheetTitle } from '@/shared/components/ui/sheet';
import { Badge } from '@/shared/components/ui/badge';
import { useAuth } from '@/modules/auth/hooks/AuthProvider';
import { initials } from '@/shared/lib/utils';
import { navigableItems } from './Sidebar';

interface MobileMoreMenuProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Passed down rather than re-fetched - AppLayout already holds it. */
  avatarSrc: string | null;
  onSignOut: () => void;
}

/**
 * CR-062. The "More" tab's destination.
 *
 * It used to open the desktop rail verbatim: deep navy chrome, 36px rows,
 * dense uppercase section headers, sliding in from the LEFT edge while the
 * button that summons it sits at the bottom-right. Three things wrong at once
 * - it looked like a desktop sidebar on a phone, it was painted in the one
 * palette the rest of the mobile UI deliberately avoids (the rail's navy is
 * chrome beside a bright working area; a phone has no such contrast to draw),
 * and it arrived from the wrong direction.
 *
 * Now it rises from the tab bar it belongs to, on the page background, as a
 * grid of destinations. A grid rather than a list because these are ~20 short,
 * equally-weighted places to go: four columns fit them nearly all above the
 * fold, where a list would have needed scrolling to reach Settings.
 *
 * `navigableItems` is reused rather than re-deriving the nav - it already
 * filters by permission and by whether a module is actually built, and it was
 * written for exactly this kind of second consumer (the command palette was
 * the first).
 */
export function MobileMoreMenu({ open, onOpenChange, avatarSrc, onSignOut }: MobileMoreMenuProps) {
  const { user, hasPermission } = useAuth();
  const navigate = useNavigate();

  // Preserve the rail's section order rather than sorting - it runs in
  // workflow order (Sales before Purchase before Inventory), which is the
  // order someone learning the software already knows.
  const sections: { title: string; items: ReturnType<typeof navigableItems> }[] = [];
  navigableItems(hasPermission).forEach((item) => {
    const existing = sections.find((section) => section.title === item.section);
    if (existing) existing.items.push(item);
    else sections.push({ title: item.section, items: [item] });
  });

  const go = (to: string) => {
    onOpenChange(false);
    navigate(to);
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="bottom"
        showClose={false}
        className="bg-background"
        style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
      >
        <SheetTitle className="sr-only">More destinations</SheetTitle>

        {/* Grab handle - the one affordance that says "this sheet dismisses". */}
        <div className="flex justify-center pt-2.5" aria-hidden>
          <span className="h-1 w-9 rounded-full bg-border" />
        </div>

        <div className="flex items-center gap-3 px-4 pb-3 pt-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary/10 text-sm font-semibold text-primary">
            {avatarSrc ? (
              <img src={avatarSrc} alt="" className="h-full w-full object-cover object-[50%_28%]" />
            ) : (
              initials(user?.fullName)
            )}
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-sm font-semibold">{user?.fullName}</span>
            <span className="block truncate text-xs text-muted-foreground">
              {user?.email ?? user?.mobileNo}
            </span>
          </span>
          {user?.roleName ? <Badge variant="secondary">{user.roleName}</Badge> : null}
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-2 pb-2">
          {sections.map((section) => (
            <div key={section.title} className="mb-1">
              <p className="px-2 pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                {section.title}
              </p>
              <ul className="grid grid-cols-4 gap-1">
                {section.items.map(({ to, label, icon: Icon }) => (
                  <li key={to}>
                    <button
                      type="button"
                      onClick={() => go(to)}
                      className="flex h-full w-full flex-col items-center gap-1.5 rounded-lg px-1 py-2.5
                                 text-center transition-colors active:bg-accent"
                    >
                      <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-muted text-foreground">
                        <Icon className="h-[1.125rem] w-[1.125rem]" aria-hidden />
                      </span>
                      <span className="text-[11px] font-medium leading-tight text-muted-foreground">
                        {label}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="border-t px-2 py-2">
          <button
            type="button"
            onClick={() => { onOpenChange(false); onSignOut(); }}
            className="flex min-h-[2.75rem] w-full items-center gap-3 rounded-lg px-3 text-sm
                       font-medium text-destructive transition-colors active:bg-destructive/10"
          >
            <LogOut className="h-4 w-4" aria-hidden />
            Sign out
          </button>
        </div>
      </SheetContent>
    </Sheet>
  );
}
