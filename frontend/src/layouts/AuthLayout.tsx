import type { ComponentType, SVGProps } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import {
  Package, ShieldCheck, Smartphone, Upload, Wallet, Zap,
} from 'lucide-react';
import { ModeToggle } from '@/theme/ModeToggle';
import { BrandMark } from '@/shared/components/BrandMark';
import { AUTH_ROUTES } from '@/modules/auth/constants';
import { AuthHeroBackdrop } from './AuthHeroBackdrop';

/**
 * CR-081. The approved sign-in design: a 50/50 split, a forest-green hero
 * over a softly blurred shop interior, and a white card on the right.
 *
 * The fifth shape of this panel, and this time it was built to a design the
 * owner signed off on the canvas first - not to a written spec interpreted
 * in code. The four earlier shapes (CR-062 strip, CR-069 glass tiles, an
 * uncommitted "editorial" pass) were each read from a brief and each judged
 * on the render. This one was judged on the render BEFORE it was code.
 *
 * **Every colour comes from a token.** The design was drawn in the Emerald
 * theme's resolved values - forest `--sidebar`, emerald `--primary`, bright
 * `--sidebar-active` - and maps straight back. Eleven themes in
 * `colorThemes.ts`, each light and dark; a hardcoded green would match
 * nothing on the rose or ocean theme. On Emerald this is pixel-for-pixel the
 * approved canvas; on every other theme it is the same design in that shop's
 * colours, which is the only version that is correct for all of them.
 *
 * `--sidebar` on the hero and the phone band is deliberate. CR-061 forbids it
 * for *mobile chrome* because a navy slab is a third of a phone screen; the
 * band here is ~110px - the mark, the name, one line - and the approved phone
 * artboard is exactly that.
 *
 * Entrance animations carry no `motion-safe:` prefix because index.css
 * already neutralises every animation under `prefers-reduced-motion: reduce`.
 */

interface Capability {
  icon: ComponentType<SVGProps<SVGSVGElement>>;
  text: string;
}

interface HeroCopy {
  eyebrow: string;
  headline: string;
  /** The word(s) of the headline drawn in the accent colour. */
  accent: string;
  about: string;
  capabilities: Capability[];
}

/*
 * Sign-in and registration are read by different people: a returning owner
 * opening the till, and someone deciding whether to start at all.
 *
 * Every line is a claim about shipped software. "Credit notes" is
 * INVOICE_CREDIT_NOTE in PermissionCode; "reorder alerts" is the CR-053
 * low-stock job; "two-factor" is CR-058; "bulk import" is /v1/products/import.
 */
const HERO: Record<'login' | 'register', HeroCopy> = {
  login: {
    eyebrow: 'Built for hardware shops in India',
    headline: 'Run the whole shop from',
    accent: 'one place.',
    about:
      'Hardware ERP brings billing, stock, purchases, payments and your team '
      + 'together in a single system — so the counter, the godown and the '
      + 'books always agree.',
    capabilities: [
      { icon: RupeeInvoice, text: 'GST invoices, quotations and credit notes' },
      { icon: Package, text: 'Live stock with reorder alerts' },
      { icon: Wallet, text: 'Payments, customer credit and expenses' },
      { icon: ShieldCheck, text: 'Roles, audit trail and two-factor sign-in' },
    ],
  },
  register: {
    eyebrow: 'Start your shop',
    headline: 'Set up in minutes,',
    accent: 'not weeks.',
    about:
      'Register the shop, add your team, bring your catalogue in from a '
      + 'sheet and raise your first GST invoice the same day.',
    capabilities: [
      { icon: Zap, text: 'No card required to start' },
      { icon: Upload, text: 'Bulk import your catalogue from a sheet' },
      { icon: Smartphone, text: 'The same shop on phone and desktop' },
      { icon: ShieldCheck, text: 'Two-factor and roles from day one' },
    ],
  },
};

/** The rupee-on-a-document icon from the design; lucide has no rupee invoice. */
function RupeeInvoice(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M6 4h9M6 8h9M13.5 8c0 2.5-2 4.5-4.5 4.5H6l6 7.5" />
    </svg>
  );
}

export function AuthLayout() {
  const { pathname } = useLocation();
  const hero = HERO[pathname === AUTH_ROUTES.register ? 'register' : 'login'];

  return (
    <div className="flex min-h-dvh flex-col lg:flex-row">
      {/* ================= LEFT - brand / hero (lg and up) ================= */}
      <aside
        className="relative hidden overflow-hidden bg-sidebar text-sidebar-foreground
                   lg:flex lg:w-1/2 lg:flex-col lg:justify-between lg:px-14 lg:py-12 xl:px-16 xl:py-14"
      >
        <AuthHeroBackdrop />
        {/* The overlay keeps the photo subordinate and the text legible; it is
            token-driven so the panel's hue follows the shop's theme. */}
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 bg-gradient-to-b from-sidebar/60 via-sidebar/80 to-sidebar/95"
        />

        {/* Top: brand */}
        <div className="relative flex flex-col gap-3.5 animate-in fade-in slide-in-from-left-2 duration-500">
          <div className="flex items-center gap-3">
            <BrandMark
              size={44}
              className="shadow-[0_0_0_1px_hsl(var(--sidebar-active)/0.28),0_8px_20px_-8px_hsl(var(--sidebar-active)/0.55)]"
            />
            <span className="text-[22px] font-bold leading-none tracking-tight">
              Hardware <span className="text-sidebar-active">ERP</span>
            </span>
          </div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-sidebar-muted">
            {hero.eyebrow}
          </p>
        </div>

        {/* Middle: hero */}
        <div className="relative max-w-[560px] space-y-9 animate-in fade-in slide-in-from-bottom-2 duration-700">
          <div className="space-y-5">
            <h1 className="text-balance text-[44px] font-bold leading-[1.08] tracking-[-0.025em] xl:text-[54px]">
              {hero.headline} <span className="text-sidebar-active">{hero.accent}</span>
            </h1>
            <p className="max-w-[520px] text-pretty text-[17px] leading-relaxed text-sidebar-foreground/75">
              {hero.about}
            </p>
          </div>

          <ul className="grid grid-cols-2 gap-x-6 gap-y-5">
            {hero.capabilities.map((item, index) => (
              <li
                key={item.text}
                className="flex items-start gap-3.5 animate-in fade-in slide-in-from-bottom-1"
                style={{ animationDelay: `${200 + index * 60}ms`, animationFillMode: 'both' }}
              >
                <span
                  className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px]
                             border border-sidebar-active/30 bg-sidebar-active/10 text-sidebar-active
                             shadow-[0_0_22px_-6px_hsl(var(--sidebar-active)/0.55)]"
                >
                  <item.icon className="h-5 w-5" aria-hidden />
                </span>
                <span className="pt-2 text-[15px] leading-snug text-sidebar-foreground/90">{item.text}</span>
              </li>
            ))}
          </ul>
        </div>

        {/* Bottom: slogan. The only decorative face on the page, and it is a
            system cursive rather than a webfont - a self-hosted shop has no
            business fetching Google Fonts to sign in. */}
        <p
          className="relative text-[30px] leading-[1.15] text-sidebar-active/85 animate-in fade-in duration-1000"
          style={{ fontFamily: "'Segoe Script', 'Bradley Hand', 'Brush Script MT', 'Comic Sans MS', cursive" }}
        >
          Smarter tools.<br />Stronger businesses.
        </p>
      </aside>

      {/* ================= RIGHT - sign in ================= */}
      <main className="relative flex flex-1 flex-col bg-muted/30">
        {/* Soft ambient shapes in two corners, from the shop's accent. Confined
            to their own clipped layer - each blob is deliberately positioned
            half off-canvas, and without a clipping boundary that pushed the
            *document's* scrollable area past the viewport on every ordinary
            screen size, forcing a real scrollbar under a page with nothing
            left to show. Clipping here changes nothing visible: the blobs
            still bleed to the same edges inside the frame. */}
        <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden">
          <div
            className="absolute -right-32 -top-36 hidden h-[460px] w-[460px] rounded-full lg:block"
            style={{ background: 'radial-gradient(closest-side, hsl(var(--primary) / 0.12), transparent)' }}
          />
          <div
            className="absolute -bottom-40 -left-36 hidden h-[480px] w-[480px] rounded-full lg:block"
            style={{ background: 'radial-gradient(closest-side, hsl(var(--primary) / 0.11), transparent)' }}
          />
        </div>

        {/* Below lg the hero collapses to this band: the mark, the name and
            the one line that says who it is for. The pitch stays on desktop. */}
        <header className="relative flex items-center justify-between gap-3 bg-sidebar px-5 py-5 text-sidebar-foreground lg:hidden">
          <div className="flex min-w-0 flex-col gap-2.5">
            <div className="flex items-center gap-2.5">
              <BrandMark size={40} className="shadow-[0_0_0_1px_hsl(var(--sidebar-active)/0.28),0_8px_20px_-8px_hsl(var(--sidebar-active)/0.55)]" />
              <span className="truncate text-[19px] font-bold tracking-tight">
                Hardware <span className="text-sidebar-active">ERP</span>
              </span>
            </div>
            <p className="text-[10.5px] font-semibold uppercase tracking-[0.16em] text-sidebar-muted">{hero.eyebrow}</p>
          </div>
          <ModeToggle />
        </header>

        {/* Desktop: the toggle floats top-right, off the card. */}
        <div className="absolute right-8 top-7 z-10 hidden lg:block">
          <ModeToggle />
        </div>

        {/* Top-aligned on a phone (the artboard has the card just under the band; centring
            it leaves a dead gap above), centred beside the hero on desktop. */}
        <div className="relative flex flex-1 items-start justify-center px-4 py-6 lg:items-center lg:px-8 lg:py-12">
          {/*
            No width cap here - each page's AuthCard sets it (max-w-[520px] for
            a short form, max-w-xl for Register's wizard). A cap on this wrapper
            silently clipped every page's own max-w-* once before.
          */}
          <div className="w-full">
            <Outlet />
          </div>
        </div>
      </main>
    </div>
  );
}
