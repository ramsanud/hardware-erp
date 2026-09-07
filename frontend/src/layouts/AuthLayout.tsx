import { Outlet } from 'react-router-dom';
import { Wrench } from 'lucide-react';
import { ModeToggle } from '@/theme/ModeToggle';
import { APP_NAME, APP_TAGLINE, APP_VERSION } from '@/shared/constants';

/**
 * CR-062. A slim brand strip, not a billboard.
 *
 * This panel used to take 44-50% of the viewport and carry a gradient, a
 * hairline grid, a radial wash, a headline, four captioned capabilities and a
 * three-column Counter/Godown/Accounts row - all of it opposite a max-w-sm
 * card adrift in an empty half. The marketing outweighed the two-field form
 * that is the entire reason anyone opens this page.
 *
 * What survives is what a returning user actually needs: proof they are on the
 * right product, in the right shop's colours. Everything that argued the
 * product's case to someone who has already decided to sign in is gone. The
 * gradient stays because it carries the active colour theme (CR-033) and costs
 * one CSS declaration.
 *
 * Still dropped entirely below lg - on a phone the form is the only thing that
 * matters, and any panel above it just pushes it under the fold.
 */
export function AuthLayout() {
  return (
    <div className="flex min-h-dvh flex-col lg:flex-row">
      <aside
        className="relative hidden overflow-hidden p-8 text-primary-foreground
                   lg:flex lg:w-[30%] lg:max-w-sm lg:flex-col lg:justify-between"
        style={{
          background:
            'linear-gradient(165deg, hsl(var(--primary)) 0%, hsl(var(--primary) / 0.9) 60%, hsl(var(--chart-3)) 140%)',
        }}
      >
        {/* One soft radial for depth. aria-hidden - it carries no meaning. */}
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              'radial-gradient(120% 70% at 20% 0%, rgb(255 255 255 / 0.14) 0%, transparent 60%)',
          }}
        />

        <div className="relative flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/15 ring-1 ring-white/20">
            <Wrench className="h-5 w-5" aria-hidden />
          </span>
          <span className="min-w-0">
            <span className="block truncate text-base font-semibold leading-tight">{APP_NAME}</span>
            <span className="block truncate text-xs text-primary-foreground/70">{APP_TAGLINE}</span>
          </span>
        </div>

        {/*
          One sentence, kept from the old panel because it was the strongest
          line on it - it names the owner's own world back to them instead of
          listing features they can already see in the app.
        */}
        <p className="relative text-[1.35rem] font-medium leading-snug tracking-tight">
          One system for the counter, the godown and the books.
        </p>

        <p className="relative text-[0.78rem] tabular-nums text-primary-foreground/55">
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
