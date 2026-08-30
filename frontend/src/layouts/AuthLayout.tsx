import { Outlet } from 'react-router-dom';
import {
  BarChart3, Boxes, ReceiptText, ShieldCheck, Wrench, type LucideIcon,
} from 'lucide-react';
import { ModeToggle } from '@/theme/ModeToggle';
import { APP_NAME, APP_TAGLINE, APP_VERSION } from '@/shared/constants';

interface Capability {
  icon: LucideIcon;
  title: string;
  detail: string;
}

/**
 * Four capabilities, each one line. The previous three ("Per-shop data
 * isolation", "Permission-based access", "Full audit trail") described the
 * security MODEL rather than what the product does - true, but it answered a
 * question a shop owner had not asked yet. Security now sits last, where it
 * reassures rather than leads.
 */
const CAPABILITIES: Capability[] = [
  {
    icon: ReceiptText,
    title: 'Sales & billing',
    detail: 'Quotations, invoices and payments.',
  },
  {
    icon: Boxes,
    title: 'Inventory control',
    detail: 'Products, stock, categories and brands.',
  },
  {
    icon: BarChart3,
    title: 'Business insights',
    detail: 'Sales, revenue and stock performance.',
  },
  {
    icon: ShieldCheck,
    title: 'Secure & organised',
    detail: 'Role-based access and full audit trails.',
  },
];

/**
 * The three places a hardware shop actually runs from. Kept from the old
 * "One system for the counter, the godown and the books" line, which was the
 * strongest sentence on the page - it names the owner's own world back to
 * them. It is now a three-column row rather than a passing clause.
 */
const WORKPLACES = [
  { place: 'Counter', covers: 'Sales & billing' },
  { place: 'Godown', covers: 'Stock & inventory' },
  { place: 'Accounts', covers: 'Payments & reports' },
];

/**
 * Two-column sign-in. The decorative panel is dropped below lg - on a phone
 * the form is the only thing that matters, and a gradient above it would just
 * push it under the fold.
 */
export function AuthLayout() {
  return (
    <div className="flex min-h-dvh flex-col lg:flex-row">
      <aside
        className="relative hidden overflow-hidden p-10 text-primary-foreground
                   lg:flex lg:w-[44%] lg:flex-col lg:justify-between xl:w-1/2 xl:p-14"
        style={{
          background:
            'linear-gradient(160deg, hsl(var(--primary)) 0%, hsl(var(--primary) / 0.88) 55%, hsl(var(--chart-3)) 135%)',
        }}
      >
        {/*
          Two flat, cheap layers instead of blurred blobs: a hairline grid for
          structure and one soft radial for depth. Both are pure CSS with no
          extra paint cost, and neither competes with the form for attention.
          aria-hidden because they carry no meaning.
        */}
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 opacity-[0.07]"
          style={{
            backgroundImage:
              'linear-gradient(to right, #fff 1px, transparent 1px),'
              + 'linear-gradient(to bottom, #fff 1px, transparent 1px)',
            backgroundSize: '56px 56px',
          }}
        />
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              'radial-gradient(120% 80% at 15% 0%, rgb(255 255 255 / 0.16) 0%, transparent 60%)',
          }}
        />

        {/* ---- identity ---- */}
        <div className="relative flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/15 ring-1 ring-white/20">
            <Wrench className="h-5 w-5" aria-hidden />
          </span>
          <span>
            <span className="block text-lg font-semibold leading-tight">{APP_NAME}</span>
            <span className="block text-xs text-primary-foreground/70">{APP_TAGLINE}</span>
          </span>
        </div>

        {/* ---- proposition ---- */}
        <div className="relative max-w-lg">
          <h1 className="text-[2rem] font-semibold leading-[1.15] tracking-tight xl:text-[2.4rem]">
            Run your hardware shop with confidence.
          </h1>
          <p className="mt-4 text-[0.975rem] leading-relaxed text-primary-foreground/75">
            Manage sales, purchases, inventory, customers and payments — all from one place.
          </p>

          <ul className="mt-9 grid gap-x-8 gap-y-5 sm:grid-cols-2">
            {CAPABILITIES.map(({ icon: Icon, title, detail }) => (
              <li key={title} className="flex gap-3">
                <span
                  className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg
                             bg-white/12 ring-1 ring-white/15"
                >
                  <Icon className="h-4 w-4" aria-hidden />
                </span>
                <span className="min-w-0">
                  <span className="block text-sm font-medium leading-snug">{title}</span>
                  <span className="block text-[0.8rem] leading-snug text-primary-foreground/65">
                    {detail}
                  </span>
                </span>
              </li>
            ))}
          </ul>

          <div className="mt-10 grid grid-cols-3 gap-4 border-t border-white/15 pt-6">
            {WORKPLACES.map(({ place, covers }) => (
              <div key={place}>
                <p className="text-[0.7rem] font-medium uppercase tracking-[0.14em] text-primary-foreground/55">
                  {place}
                </p>
                <p className="mt-1 text-[0.82rem] leading-snug text-primary-foreground/85">{covers}</p>
              </div>
            ))}
          </div>
        </div>

        {/* ---- footer ---- */}
        <div className="relative flex items-baseline justify-between text-[0.78rem] text-primary-foreground/55">
          <span>Built for modern hardware businesses</span>
          <span className="tabular-nums">Version {APP_VERSION}</span>
        </div>
      </aside>

      <main className="flex flex-1 flex-col">
        <header className="flex items-center justify-between p-4 lg:justify-end lg:p-6">
          <div className="flex items-center gap-2 lg:hidden">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10">
              <Wrench className="h-4 w-4 text-primary" aria-hidden />
            </span>
            <span className="font-semibold">{APP_NAME}</span>
          </div>
          <ModeToggle />
        </header>
        <div className="flex flex-1 items-center justify-center px-4 pb-12">
          {/* max-w-sm on purpose: a wider sign-in card reads as a form to fill
              in, not a door to walk through. */}
          <div className="w-full max-w-sm">
            <Outlet />
          </div>
        </div>
      </main>
    </div>
  );
}
