import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { Loader2 } from 'lucide-react';
import { cn } from '@/shared/lib/utils';

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        default: 'control-surface bg-primary text-primary-foreground hover:bg-primary/90',
        destructive: 'bg-destructive text-destructive-foreground hover:bg-destructive/90',
        outline: 'control-surface border border-input bg-background hover:bg-accent hover:text-accent-foreground',
        secondary: 'control-surface bg-secondary text-secondary-foreground hover:bg-secondary/80',
        ghost: 'hover:bg-accent hover:text-accent-foreground',
        link: 'text-primary underline-offset-4 hover:underline',
        /*
         * CR-069, for the auth screens' primary CTA. The design asked for
         * `from-blue-600 to-indigo-600`; a fixed blue would ignore all eleven
         * colour themes, so the gradient is mixed from the theme's own tokens.
         *
         * The far stop is built in two moves, and the second one is not
         * decoration - it is what keeps the button readable:
         *
         *   1. shift the hue 40% toward --chart-3, which is what makes it read
         *      as a gradient rather than a flat fill;
         *   2. correct the lightness AWAY from the text on top - darker under
         *      light mode's near-white --primary-foreground, lighter under dark
         *      mode's near-black one.
         *
         * Step 2 was added after measuring. --chart-3 is lighter than --primary
         * in every light theme, so hue-shifting alone dragged the far end down
         * to 3.52:1 on teal/light - below AA for 14px text - and no mix weight
         * fixed it without killing the gradient. With the correction the worst
         * case across all 11 themes x 2 modes is 6.94:1 (indigo/dark), which is
         * better than the plain `default` variant's own worst case of 4.29:1
         * (amber/light). Re-measure before changing these numbers.
         *
         * `bg-primary` is not redundant: it is the fallback. A browser without
         * color-mix() drops the whole background-image declaration as invalid
         * and lands on a solid primary button rather than a transparent one.
         */
        gradient:
          'control-surface bg-primary text-primary-foreground shadow-lg shadow-primary/25 '
          + 'bg-[linear-gradient(110deg,hsl(var(--primary)),color-mix(in_oklab,color-mix(in_oklab,hsl(var(--primary))_60%,hsl(var(--chart-3)))_70%,#000))] '
          + 'dark:bg-[linear-gradient(110deg,hsl(var(--primary)),color-mix(in_oklab,color-mix(in_oklab,hsl(var(--primary))_60%,hsl(var(--chart-3)))_78%,#fff))] '
          + 'transition-all hover:brightness-110 active:scale-[0.99]',
      },
      size: {
        default: 'h-10 px-4 py-2',
        sm: 'h-9 rounded-md px-3',
        lg: 'h-11 rounded-md px-6',
        icon: 'h-10 w-10',
      },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
  loading?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, loading = false, disabled, children, ...props }, ref) => {
    const Comp = asChild ? Slot : 'button';
    return (
      <Comp
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        disabled={disabled || loading}
        aria-busy={loading || undefined}
        {...props}
      >
        {asChild ? children : (
          <>
            {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
            {children}
          </>
        )}
      </Comp>
    );
  },
);
Button.displayName = 'Button';

export { Button, buttonVariants };
