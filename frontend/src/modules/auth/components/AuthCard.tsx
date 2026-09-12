import type { ReactNode } from 'react';
import { Lock } from 'lucide-react';
import { BrandMark } from '@/shared/components/BrandMark';
import { cn } from '@/shared/lib/utils';

/**
 * CR-081. The white card on the right of the sign-in design.
 *
 * One shell for every screen under AuthLayout - sign in, verification,
 * enrolment, register, forgot and reset - so the brand header, the radius,
 * the shadow and the trust line are identical on all of them and a page
 * cannot quietly drift to its own look. Each page supplies only its heading
 * and its form.
 *
 * Widths stay the page's call (`max-w-[520px]` for a short form, `max-w-xl`
 * for Register's wizard) because a cap here once silently clipped every
 * page's own max-w-* - see the note in AuthLayout.
 */
interface AuthCardProps {
  /** e.g. "Welcome back!" - rendered centred, as the design has it. */
  title: string;
  description?: ReactNode;
  children: ReactNode;
  className?: string;
  /** Hide the brand header on a step that is already inside a flow (Register's wizard). */
  brand?: boolean;
}

export function AuthCard({ title, description, children, className, brand = true }: AuthCardProps) {
  return (
    <section
      aria-labelledby="auth-card-title"
      className={cn(
        'mx-auto w-full max-w-[520px] rounded-[20px] border border-border/80 bg-card text-card-foreground',
        'px-6 py-8 shadow-[0_24px_60px_-28px_hsl(var(--sidebar)/0.28),0_1px_2px_hsl(var(--sidebar)/0.06)]',
        'animate-in fade-in slide-in-from-bottom-2 duration-500 sm:px-12 sm:pb-10 sm:pt-11',
        className,
      )}
    >
      {brand ? (
        <div className="mb-7 flex flex-col items-center gap-2.5 text-center">
          <BrandMark size={52} className="shadow-[0_10px_24px_-12px_hsl(var(--primary)/0.6)]" />
          <div className="flex flex-col items-center gap-1.5">
            <span className="text-lg font-bold tracking-tight">Hardware ERP</span>
            <span className="text-[10.5px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
              Smarter operations. Higher growth.
            </span>
          </div>
        </div>
      ) : null}

      <header className="mb-7 space-y-1.5 text-center">
        <h1 id="auth-card-title" className="text-[28px] font-bold leading-tight tracking-[-0.02em]">
          {title}
        </h1>
        {description ? (
          <p className="text-[15px] leading-normal text-muted-foreground">{description}</p>
        ) : null}
      </header>

      {children}

      <p className="mt-7 flex items-center justify-center gap-2 text-[13px] text-muted-foreground">
        <Lock className="h-3.5 w-3.5 text-primary" aria-hidden />
        Your data is secure and encrypted
      </p>
    </section>
  );
}
