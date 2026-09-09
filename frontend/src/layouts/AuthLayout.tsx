import { Outlet, useLocation } from 'react-router-dom';
import {
  Package, Receipt, ShieldCheck, Smartphone, TrendingUp, Upload, Wrench, Zap,
} from 'lucide-react';
import { ModeToggle } from '@/theme/ModeToggle';
import { APP_NAME, APP_TAGLINE, APP_VERSION } from '@/shared/constants';
import { AUTH_ROUTES } from '@/modules/auth/constants';

/**
 * CR-069. A premium brand panel that still follows the shop's colour theme.
 *
 * History matters here. CR-062 cut this panel down from a 44-50% billboard
 * carrying a gradient, a hairline grid, a radial wash, a headline, four
 * captioned capabilities and a three-column stat row - the owner's verdict on
 * that version was "too absurd and too complex". This is a deliberate,
 * approved return to a richer panel, and the two things that made the old one
 * fail are the two things kept out: it is not half the viewport, and it
 * carries no invented social proof.
 *
 * **Every colour comes from a token.** The spec that prompted this asked for
 * `bg-[#0b0f19]` and a `from-blue-600 to-indigo-600` CTA. Hardcoding those
 * would have given a shop on the emerald or rose theme a blue sign-in page
 * matching nothing else in their app - there are eleven themes in
 * `colorThemes.ts`, each with a light and dark variant. The panel is painted
 * from `--sidebar`, which is the one token guaranteed deep and dark in all
 * twenty-two combinations (lightness 6-13%) and always paired with a near-white
 * `--sidebar-foreground`. That gives the "rich deep dark" the design wanted
 * *and* a hue that shifts with the theme.
 *
 * The `--sidebar` tokens are used here and not on mobile chrome on purpose -
 * CR-061 forbids the latter because a navy slab is a third of a phone screen.
 * This panel is `lg`-only and never renders on a phone, so that reasoning
 * does not reach it.
 *
 * Entrance animations carry no `motion-safe:` prefix because index.css already
 * neutralises every animation under `prefers-reduced-motion: reduce`.
 */

interface HeroFeature {
  icon: typeof Package;
  title: string;
  caption: string;
  /**
   * Icon glow classes. Written out in full rather than composed from a
   * `chart-${n}` template because Tailwind's JIT scans source text - an
   * interpolated class name is never generated. Chart tokens are also the only
   * ones that stay visibly distinct from each other across all eleven themes.
   */
  tone: string;
}

interface HeroContent {
  lead: string;
  accent: string;
  features: HeroFeature[];
}

/*
 * Sign-in and registration are read by different people in different moods -
 * a returning owner opening the till versus someone deciding whether to start
 * at all - so the panel argues a different point on each.
 *
 * Every caption below is a claim about software that actually exists. An
 * earlier draft of the registration copy promised a "10,000+ default products
 * catalog"; the only product seed in the repo is V902, twelve dev-only rows
 * that never load in production. Bulk import is the real version of that
 * promise, so that is what it says.
 */
const HERO: Record<'login' | 'register', HeroContent> = {
  login: {
    lead: 'One system for',
    accent: 'the counter, the godown and the books.',
    features: [
      { icon: Receipt, title: 'Billing & GST', caption: 'Invoices, quotations, credit notes', tone: 'text-chart-1 bg-chart-1/10 ring-chart-1/25' },
      { icon: Package, title: 'Stock & godown', caption: 'Live quantities and reorder alerts', tone: 'text-chart-2 bg-chart-2/10 ring-chart-2/25' },
      { icon: TrendingUp, title: 'Insights', caption: 'Revenue, categories and top accounts', tone: 'text-chart-3 bg-chart-3/10 ring-chart-3/25' },
      { icon: ShieldCheck, title: 'Secure', caption: 'Roles, audit trail and two-factor', tone: 'text-chart-4 bg-chart-4/10 ring-chart-4/25' },
    ],
  },
  register: {
    lead: 'Set up your hardware business in',
    accent: 'under 2 minutes.',
    features: [
      { icon: Zap, title: 'Instant setup', caption: 'No card required to start', tone: 'text-chart-1 bg-chart-1/10 ring-chart-1/25' },
      { icon: Upload, title: 'Bulk import', caption: 'Bring your catalogue in from a sheet', tone: 'text-chart-2 bg-chart-2/10 ring-chart-2/25' },
      { icon: Smartphone, title: 'Phone & desktop', caption: 'The same shop on every device', tone: 'text-chart-3 bg-chart-3/10 ring-chart-3/25' },
      { icon: ShieldCheck, title: 'Bank-grade security', caption: 'Two-factor, roles and audit logs', tone: 'text-chart-4 bg-chart-4/10 ring-chart-4/25' },
    ],
  },
};

export function AuthLayout() {
  const { pathname } = useLocation();
  const hero = HERO[pathname === AUTH_ROUTES.register ? 'register' : 'login'];

  return (
    <div className="flex min-h-dvh flex-col lg:flex-row">
      <aside
        className="relative hidden overflow-hidden bg-sidebar p-10 text-sidebar-foreground
                   lg:flex lg:w-[38%] lg:max-w-md lg:flex-col lg:justify-between lg:gap-10"
      >
        {/*
          Ambient mesh. Two soft radials plus a hairline grid, all drawn from
          tokens so they carry the active theme's hue. aria-hidden throughout -
          none of it means anything.
        */}
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0"
          style={{
            background: [
              'radial-gradient(70% 45% at 15% 0%, hsl(var(--chart-1) / 0.28) 0%, transparent 65%)',
              'radial-gradient(60% 45% at 100% 100%, hsl(var(--chart-3) / 0.22) 0%, transparent 60%)',
            ].join(', '),
          }}
        />
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0"
          style={{
            backgroundImage: [
              'repeating-linear-gradient(0deg, hsl(var(--sidebar-foreground) / 0.045) 0 1px, transparent 1px 64px)',
              'repeating-linear-gradient(90deg, hsl(var(--sidebar-foreground) / 0.045) 0 1px, transparent 1px 64px)',
            ].join(', '),
            maskImage: 'radial-gradient(120% 90% at 30% 20%, #000 0%, transparent 75%)',
            WebkitMaskImage: 'radial-gradient(120% 90% at 30% 20%, #000 0%, transparent 75%)',
          }}
        />

        {/* Brand mark. The glass treatment is token-tinted, not white-on-guess. */}
        <div className="relative flex items-center gap-3 animate-in fade-in slide-in-from-left-2 duration-500">
          <span
            className="flex h-11 w-11 items-center justify-center rounded-2xl border
                       border-sidebar-foreground/15 bg-sidebar-foreground/10 backdrop-blur-md"
          >
            <Wrench className="h-5 w-5" aria-hidden />
          </span>
          <span className="min-w-0">
            <span className="block truncate text-base font-semibold leading-tight">{APP_NAME}</span>
            <span className="block truncate text-xs text-sidebar-muted">{APP_TAGLINE}</span>
          </span>
        </div>

        <div className="relative space-y-8">
          <h1 className="text-4xl font-extrabold leading-[1.1] tracking-tight animate-in fade-in slide-in-from-left-3 duration-700">
            {hero.lead}{' '}
            {/*
              The gradient mask runs chart-1 -> chart-3 specifically: both sit
              at 55%+ lightness in every light theme and 62%+ in every dark one,
              so the text stays legible on the dark panel across all eleven.
              chart-2 drops to 42% in some light themes and was too dim here.
            */}
            <span className="bg-gradient-to-r from-chart-1 to-chart-3 bg-clip-text text-transparent">
              {hero.accent}
            </span>
          </h1>

          <ul className="grid grid-cols-2 gap-3">
            {hero.features.map((feature, index) => (
              <li
                key={feature.title}
                className="rounded-2xl border border-sidebar-foreground/10 bg-sidebar-foreground/5 p-4
                           backdrop-blur-md transition-colors hover:bg-sidebar-foreground/10
                           animate-in fade-in slide-in-from-bottom-2"
                style={{ animationDelay: `${150 + index * 70}ms`, animationFillMode: 'both' }}
              >
                <span
                  className={`mb-3 flex h-9 w-9 items-center justify-center rounded-xl ring-1 ${feature.tone}`}
                >
                  <feature.icon className="h-[1.05rem] w-[1.05rem]" aria-hidden />
                </span>
                <span className="block text-sm font-semibold leading-tight">{feature.title}</span>
                <span className="mt-1 block text-xs leading-snug text-sidebar-muted">{feature.caption}</span>
              </li>
            ))}
          </ul>
        </div>

        <p className="relative text-[0.78rem] tabular-nums text-sidebar-muted">
          Version {APP_VERSION}
        </p>
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
        <div className="flex flex-1 items-center justify-center px-4 pb-12 lg:px-8">
          {/*
            No width cap here - each page's own Card sets it (max-w-md for a
            short form like Sign in, max-w-xl for Register's wizard). A cap on
            this wrapper used to silently clip every page's own max-w-*, which
            is why RegisterPage's max-w-xl was dead CSS until this changed.
          */}
          <div className="w-full">
            <Outlet />
          </div>
        </div>
      </main>
    </div>
  );
}
