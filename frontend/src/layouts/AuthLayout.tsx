import { Outlet } from 'react-router-dom';
import { Check, Wrench } from 'lucide-react';
import { ModeToggle } from '@/theme/ModeToggle';
import { APP_NAME } from '@/shared/constants';

const HIGHLIGHTS = [
  'Per-shop data isolation',
  'Permission-based access',
  'Full audit trail',
];

/** Centred card on every size; the panel is decoration and is dropped below lg. */
export function AuthLayout() {
  return (
    <div className="flex min-h-dvh flex-col lg:flex-row">
      <aside
        className="hidden p-10 text-primary-foreground lg:flex lg:w-[42%] lg:flex-col lg:justify-between xl:w-1/2"
        style={{
          background:
            'linear-gradient(160deg, hsl(var(--primary)) 0%, hsl(var(--primary) / 0.85) 55%, hsl(var(--chart-3)) 130%)',
        }}
      >
        <div className="flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/15">
            <Wrench className="h-5 w-5" aria-hidden />
          </span>
          <span className="text-lg font-semibold">{APP_NAME}</span>
        </div>
        <div className="max-w-md">
          <p className="text-2xl font-semibold leading-snug">
            Billing, stock and accounts for your hardware shop.
          </p>
          <p className="mt-3 text-primary-foreground/70">
            One system for the counter, the godown and the books.
          </p>
          <ul className="mt-6 space-y-2.5">
            {HIGHLIGHTS.map((item) => (
              <li key={item} className="flex items-center gap-2.5 text-sm text-primary-foreground/90">
                <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-white/15">
                  <Check className="h-3 w-3" aria-hidden />
                </span>
                {item}
              </li>
            ))}
          </ul>
        </div>
        <p className="text-sm text-primary-foreground/60">Module 1 · Authentication</p>
      </aside>

      <main className="flex flex-1 flex-col">
        <header className="flex items-center justify-between p-4 lg:justify-end">
          <div className="flex items-center gap-2 lg:hidden">
            <Wrench className="h-5 w-5 text-primary" aria-hidden />
            <span className="font-semibold">{APP_NAME}</span>
          </div>
          <ModeToggle />
        </header>
        <div className="flex flex-1 items-center justify-center px-4 pb-10">
          <div className="w-full max-w-sm">
            <Outlet />
          </div>
        </div>
      </main>
    </div>
  );
}
