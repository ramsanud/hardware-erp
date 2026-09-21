import type { ComponentType, SVGProps } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight, BarChart3, FileJson, FileSpreadsheet, FileText, HardHat, KeyRound, Mail,
  MapPin, MessageCircle, MessageSquareText, Package, ScrollText, ShieldCheck, Smartphone, Upload,
  Users, Wallet, Zap,
} from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { BrandMark } from '@/shared/components/BrandMark';
import { Integration, IntegrationCard, type IntegrationTile } from '@/shared/components/ui/integration-card';
import { ModeToggle } from '@/theme/ModeToggle';
import { APP_NAME, APP_VERSION } from '@/shared/constants';
import { AUTH_ROUTES } from '@/modules/auth/constants';

/**
 * CR-100. The public landing page at `/`.
 *
 * Same brand language as the sign-in design (CR-081): a `--sidebar` hero
 * with the accent phrase in `--sidebar-active`, capability tiles in the
 * accent at 10%, the system cursive for the slogan. Everything on it is a
 * claim about shipped software - each line is traceable to a module in
 * FEATURE_REGISTRY or a CR - and there is no invented count, price,
 * testimonial or screenshot of data (rule 12). The one visual is the
 * integrations card, which draws real channels and exports.
 *
 * Lazy-loaded from the routes so `motion` is only fetched by visitors who
 * land here; a signed-in user opening the app never downloads it.
 */

interface Feature {
  icon: ComponentType<SVGProps<SVGSVGElement>>;
  title: string;
  text: string;
}

/** Each line names a built module. Check FEATURE_REGISTRY before editing. */
const FEATURES: Feature[] = [
  {
    icon: RupeeInvoice,
    title: 'GST billing',
    text: 'GST, non-GST and mixed invoices, quotations that convert to bills, credit notes and delivery challans.',
  },
  {
    icon: Package,
    title: 'Stock that agrees with the godown',
    text: 'Every movement recorded, reorder rules and low-stock alerts, purchase orders through GRN to the supplier bill.',
  },
  {
    icon: Wallet,
    title: 'Payments and customer credit',
    text: 'Full, partial and later payments, credit limits and credit days, receivables ageing by customer.',
  },
  {
    icon: BarChart3,
    title: 'Reports and GST returns',
    text: 'Day book, receivables ageing, stock valuation, purchase register and GST summary - each as PDF or Excel. GSTR-1 JSON for the return period.',
  },
  {
    icon: HardHat,
    title: 'Projects and labour',
    text: 'Cost a job across products, suppliers, invoices and worker attendance, and bill the labour through the invoice.',
  },
  {
    icon: ShieldCheck,
    title: 'Your team, your rules',
    text: 'Roles built from permissions, two-factor sign-in with backup codes, and every change kept in an audit trail.',
  },
];

/** What the product connects to. CR-080 (WhatsApp), CR-074 (email, SMS), CR-086 (Tally, PDF, Excel), CR-087 (GSTR-1), CR-076 (map). */
const INTEGRATIONS: IntegrationTile[] = [
  { id: 'whatsapp', label: 'WhatsApp', icon: MessageCircle },
  { id: 'email', label: 'Email', icon: Mail },
  { id: 'sms', label: 'SMS', icon: MessageSquareText },
  { id: 'tally', label: 'Tally', icon: FileSpreadsheet },
  { id: 'gstr1', label: 'GSTR-1 JSON', icon: FileJson },
  { id: 'map', label: 'Map', icon: MapPin },
];

const STEPS = [
  { icon: Zap, title: 'Register the shop', text: 'Shop name, your name and mobile number. No card required to start.' },
  { icon: Upload, title: 'Bring in your catalogue', text: 'Import products from a sheet - preview every line, then confirm.' },
  { icon: FileText, title: 'Raise the first invoice', text: 'Same day. Print it, or send it on WhatsApp with one tap.' },
];

const SECURITY = [
  { icon: KeyRound, text: 'Sign-in token held in memory only - never written to the browser.' },
  { icon: ShieldCheck, text: 'Two-factor sign-in with backup codes; lockout after repeated failures.' },
  { icon: ScrollText, text: 'Security events and business changes logged, with who, what and when.' },
  { icon: Users, text: 'Users and suppliers are never hard-deleted, so old bills always resolve.' },
];

/** The rupee-on-a-document icon from the sign-in design; lucide has no rupee invoice. */
function RupeeInvoice(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M6 4h9M6 8h9M13.5 8c0 2.5-2 4.5-4.5 4.5H6l6 7.5" />
    </svg>
  );
}

/*
 * Buttons on the `--sidebar` bands. The `gradient` variant mixes primary
 * toward --chart-3 (amber) and reads olive on a dark green surface; the solid
 * accent is what the sign-in hero uses for its emphasis, so it is used here
 * for the primary action. Text is the band's own surface colour, which is
 * the highest-contrast pairing the tokens offer.
 */
const HERO_PRIMARY = 'h-12 rounded-xl bg-sidebar-active px-6 text-[15px] text-sidebar shadow-lg shadow-sidebar-active/20 hover:bg-sidebar-active/90';
const HERO_SECONDARY = 'h-12 rounded-xl border-sidebar-active/30 bg-sidebar-active/10 px-6 text-[15px] text-sidebar-foreground hover:bg-sidebar-active/20 hover:text-sidebar-foreground';

function Wordmark({ size = 36 }: { size?: number }) {
  return (
    <span className="flex items-center gap-2.5">
      <BrandMark size={size} />
      <span className="text-[19px] font-bold leading-none tracking-tight">
        Hardware <span className="text-primary">ERP</span>
      </span>
    </span>
  );
}

export function LandingPage() {
  return (
    <div className="min-h-dvh bg-background text-foreground" data-landing>
      {/* ================= Header ================= */}
      <header className="sticky top-0 z-40 border-b bg-background/90 backdrop-blur supports-[backdrop-filter]:bg-background/75">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
          <Link to="/" aria-label={`${APP_NAME} home`}>
            <Wordmark />
          </Link>
          <nav aria-label="Sections" className="hidden items-center gap-7 text-sm font-medium text-muted-foreground md:flex">
            <a href="#features" className="transition-colors hover:text-foreground">Features</a>
            <a href="#how-it-works" className="transition-colors hover:text-foreground">How it works</a>
            <a href="#security" className="transition-colors hover:text-foreground">Security</a>
          </nav>
          <div className="flex items-center gap-2">
            <ModeToggle />
            <Button variant="ghost" asChild className="hidden sm:inline-flex">
              <Link to={AUTH_ROUTES.login}>Sign in</Link>
            </Button>
            <Button asChild>
              <Link to={AUTH_ROUTES.register}>
                Register your shop
                <ArrowRight className="h-4 w-4" aria-hidden />
              </Link>
            </Button>
          </div>
        </div>
      </header>

      <main>
        {/* ================= Hero ================= */}
        <section className="relative overflow-hidden bg-sidebar text-sidebar-foreground">
          <div
            aria-hidden
            className="pointer-events-none absolute -left-40 -top-40 h-[560px] w-[560px] rounded-full"
            style={{ background: 'radial-gradient(closest-side, hsl(var(--sidebar-active) / 0.18), transparent)' }}
          />
          <div
            aria-hidden
            className="pointer-events-none absolute -bottom-48 right-0 h-[520px] w-[520px] rounded-full"
            style={{ background: 'radial-gradient(closest-side, hsl(var(--primary) / 0.16), transparent)' }}
          />
          <div className="relative mx-auto grid max-w-7xl items-center gap-12 px-4 py-16 sm:px-6 lg:grid-cols-2 lg:gap-16 lg:px-8 lg:py-24">
            <div className="max-w-[600px] space-y-8 animate-in fade-in slide-in-from-bottom-2 duration-700">
              <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-sidebar-muted">
                Built for hardware shops in India
              </p>
              <div className="space-y-5">
                <h1 className="text-balance text-[40px] font-bold leading-[1.08] tracking-[-0.025em] sm:text-[50px] xl:text-[58px]">
                  Run the whole shop from <span className="text-sidebar-active">one place.</span>
                </h1>
                <p className="max-w-[540px] text-pretty text-[17px] leading-relaxed text-sidebar-foreground/75">
                  {APP_NAME} brings billing, stock, purchases, payments and your team together in a
                  single system — so the counter, the godown and the books always agree.
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <Button size="lg" asChild className={HERO_PRIMARY}>
                  <Link to={AUTH_ROUTES.register}>
                    Register your shop
                    <ArrowRight className="h-4 w-4" aria-hidden />
                  </Link>
                </Button>
                <Button
                  variant="outline"
                  size="lg"
                  asChild
                  className={HERO_SECONDARY}
                >
                  <Link to={AUTH_ROUTES.login}>Sign in</Link>
                </Button>
              </div>
              <ul className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-sidebar-foreground/80">
                {['No card required to start', 'Phone and desktop', 'Two-factor sign-in'].map((item) => (
                  <li key={item} className="flex items-center gap-2">
                    <span className="h-1.5 w-1.5 rounded-full bg-sidebar-active" aria-hidden />
                    {item}
                  </li>
                ))}
              </ul>
            </div>

            <div className="animate-in fade-in slide-in-from-bottom-3 duration-1000 lg:justify-self-end">
              <IntegrationCard
                visual={<Integration tiles={INTEGRATIONS} />}
                title="Works with what the shop already uses"
                description="Send bills, quotations and reminders on WhatsApp, email or SMS. Hand the accountant a Tally export and the GSTR-1 JSON — from the same records that raised the bill."
                to="#features"
                cta="See what's inside"
                className="shadow-[0_32px_80px_-32px_hsl(var(--sidebar-active)/0.35)]"
              />
            </div>
          </div>
        </section>

        {/* ================= Features ================= */}
        <section id="features" className="scroll-mt-16">
          <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-24">
            <div className="max-w-2xl space-y-3">
              <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-primary">What&apos;s inside</p>
              <h2 className="text-balance text-3xl font-bold tracking-tight sm:text-4xl">
                Everything from the counter to the books
              </h2>
              <p className="text-pretty text-base leading-relaxed text-muted-foreground">
                One record for each sale, purchase and payment - the same figures on the screen, the
                printed bill, the report and the return.
              </p>
            </div>
            <ul className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {FEATURES.map((feature) => (
                <li key={feature.title} className="rounded-2xl border bg-card p-6 text-card-foreground shadow-sm">
                  <span className="flex h-11 w-11 items-center justify-center rounded-xl border border-primary/20 bg-primary/10 text-primary">
                    <feature.icon className="h-5 w-5" aria-hidden />
                  </span>
                  <h3 className="mt-5 text-lg font-semibold tracking-tight">{feature.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{feature.text}</p>
                </li>
              ))}
            </ul>
          </div>
        </section>

        {/* ================= How it works ================= */}
        <section id="how-it-works" className="scroll-mt-16 border-y bg-muted/40">
          <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-24">
            <div className="grid gap-12 lg:grid-cols-[1fr_1.4fr] lg:gap-16">
              <div className="space-y-3">
                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-primary">How it works</p>
                <h2 className="text-balance text-3xl font-bold tracking-tight sm:text-4xl">
                  Set up in minutes, not weeks.
                </h2>
                <p className="text-pretty text-base leading-relaxed text-muted-foreground">
                  Register the shop, add your team, bring your catalogue in from a sheet and raise
                  your first GST invoice the same day.
                </p>
                <p className="flex items-center gap-2 pt-2 text-sm text-muted-foreground">
                  <Smartphone className="h-4 w-4 text-primary" aria-hidden />
                  The same shop on phone and desktop.
                </p>
              </div>
              <ol className="grid gap-4 sm:grid-cols-3">
                {STEPS.map((step, index) => (
                  <li key={step.title} className="relative rounded-2xl border bg-card p-6 text-card-foreground shadow-sm">
                    <span className="text-[11px] font-bold uppercase tracking-[0.14em] text-primary">Step {index + 1}</span>
                    <span className="mt-4 flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
                      <step.icon className="h-5 w-5" aria-hidden />
                    </span>
                    <h3 className="mt-4 font-semibold tracking-tight">{step.title}</h3>
                    <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">{step.text}</p>
                  </li>
                ))}
              </ol>
            </div>
          </div>
        </section>

        {/* ================= Security ================= */}
        <section id="security" className="scroll-mt-16">
          <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-24">
            <div className="grid gap-12 lg:grid-cols-[1fr_1.4fr] lg:gap-16">
              <div className="space-y-3">
                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-primary">Security</p>
                <h2 className="text-balance text-3xl font-bold tracking-tight sm:text-4xl">
                  Built like the books should be.
                </h2>
                <p className="text-pretty text-base leading-relaxed text-muted-foreground">
                  Each shop&apos;s data is separated on the server, on every request, from the
                  signed-in session - never from anything the browser sends.
                </p>
              </div>
              <ul className="grid gap-4 sm:grid-cols-2">
                {SECURITY.map((item) => (
                  <li key={item.text} className="flex items-start gap-3.5 rounded-2xl border bg-card p-5 text-card-foreground">
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                      <item.icon className="h-[18px] w-[18px]" aria-hidden />
                    </span>
                    <p className="pt-1.5 text-sm leading-relaxed text-foreground/85">{item.text}</p>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>

        {/* ================= Closing CTA ================= */}
        <section className="relative overflow-hidden bg-sidebar text-sidebar-foreground">
          <div
            aria-hidden
            className="pointer-events-none absolute left-1/2 top-1/2 h-[520px] w-[900px] -translate-x-1/2 -translate-y-1/2 rounded-full"
            style={{ background: 'radial-gradient(closest-side, hsl(var(--sidebar-active) / 0.14), transparent)' }}
          />
          <div className="relative mx-auto flex max-w-7xl flex-col items-center gap-8 px-4 py-16 text-center sm:px-6 lg:px-8 lg:py-24">
            <p
              className="text-[30px] leading-[1.15] text-sidebar-active/85"
              style={{ fontFamily: "'Segoe Script', 'Bradley Hand', 'Brush Script MT', 'Comic Sans MS', cursive" }}
            >
              Smarter tools.<br />Stronger businesses.
            </p>
            <h2 className="max-w-2xl text-balance text-3xl font-bold tracking-tight sm:text-4xl">
              Open the till on {APP_NAME} tomorrow morning.
            </h2>
            <div className="flex flex-wrap items-center justify-center gap-3">
              <Button size="lg" asChild className={HERO_PRIMARY}>
                <Link to={AUTH_ROUTES.register}>
                  Register your shop
                  <ArrowRight className="h-4 w-4" aria-hidden />
                </Link>
              </Button>
              <Button
                variant="outline"
                size="lg"
                asChild
                className={HERO_SECONDARY}
              >
                <Link to={AUTH_ROUTES.login}>Sign in</Link>
              </Button>
            </div>
          </div>
        </section>
      </main>

      {/* ================= Footer ================= */}
      <footer className="border-t">
        <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-10 sm:flex-row sm:items-center sm:justify-between sm:px-6 lg:px-8">
          <div className="space-y-2">
            <Wordmark size={32} />
            <p className="text-sm text-muted-foreground">Built for hardware shops in India. Version {APP_VERSION}.</p>
          </div>
          <nav aria-label="Footer" className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-muted-foreground">
            <a href="#features" className="hover:text-foreground">Features</a>
            <a href="#security" className="hover:text-foreground">Security</a>
            <Link to={AUTH_ROUTES.login} className="hover:text-foreground">Sign in</Link>
            <Link to={AUTH_ROUTES.register} className="hover:text-foreground">Register your shop</Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}

export default LandingPage;
