import * as React from 'react';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { cn } from '@/shared/lib/utils';

/**
 * A panel anchored to one edge of the screen, built on the same Radix Dialog
 * primitives as Dialog (so focus trapping, scroll locking and Escape all
 * behave identically).
 *
 * Exists because the mobile navigation was previously a centred <Dialog>
 * forced to the left edge with `left-0 top-0 translate-x-0`. That fought
 * DialogContent's own centring transform, so it zoomed open from the middle
 * instead of sliding in from the side, and the whole panel scrolled as one
 * block - brand and footer included. A drawer needs its own primitive, not a
 * modal wearing a costume.
 */
const Sheet = DialogPrimitive.Root;
const SheetTrigger = DialogPrimitive.Trigger;
const SheetClose = DialogPrimitive.Close;
const SheetPortal = DialogPrimitive.Portal;

const SheetOverlay = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Overlay>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Overlay>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Overlay
    ref={ref}
    className={cn(
      'fixed inset-0 z-50 bg-black/60 backdrop-blur-[2px]',
      'data-[state=open]:animate-in data-[state=closed]:animate-out',
      'data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 duration-300',
      className,
    )}
    {...props}
  />
));
SheetOverlay.displayName = 'SheetOverlay';

type SheetSide = 'left' | 'right' | 'bottom';

interface SheetContentProps
  extends React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content> {
  side?: SheetSide;
  /** Set false when the panel provides its own close affordance. */
  showClose?: boolean;
}

/*
 * Size lives here rather than on SheetContent, because a bottom sheet is the
 * opposite shape from an edge drawer - full width, content height - and a
 * shared `h-dvh w-[17rem]` cannot describe both (CR-062).
 */
const SIDE_CLASSES: Record<SheetSide, string> = {
  left: 'inset-y-0 left-0 h-dvh w-[17rem] max-w-[85vw] border-r data-[state=open]:slide-in-from-left data-[state=closed]:slide-out-to-left',
  right: 'inset-y-0 right-0 h-dvh w-[17rem] max-w-[85vw] border-l data-[state=open]:slide-in-from-right data-[state=closed]:slide-out-to-right',
  // max-h, not h: a short menu should not stretch to fill the screen.
  bottom: 'inset-x-0 bottom-0 max-h-[85dvh] w-full rounded-t-2xl border-t data-[state=open]:slide-in-from-bottom data-[state=closed]:slide-out-to-bottom',
};

const SheetContent = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Content>,
  SheetContentProps
>(({ className, children, side = 'left', showClose = true, ...props }, ref) => (
  <SheetPortal>
    <SheetOverlay />
    <DialogPrimitive.Content
      ref={ref}
      className={cn(
        // h-dvh, not h-screen: mobile browser chrome shrinks the visual
        // viewport and h-screen would push the footer under it.
        'fixed z-50 flex flex-col shadow-xl',
        'data-[state=open]:animate-in data-[state=closed]:animate-out duration-300 ease-out',
        SIDE_CLASSES[side],
        className,
      )}
      {...props}
    >
      {children}
      {showClose ? (
        <DialogPrimitive.Close
          aria-label="Close navigation"
          className="absolute right-3 top-4 rounded-md p-1.5 opacity-70 transition-colors
                     hover:bg-destructive/10 hover:text-destructive hover:opacity-100
                     focus:outline-none focus:ring-2 focus:ring-ring"
        >
          <X className="h-5 w-5" />
          <span className="sr-only">Close</span>
        </DialogPrimitive.Close>
      ) : null}
    </DialogPrimitive.Content>
  </SheetPortal>
));
SheetContent.displayName = 'SheetContent';

const SheetHeader = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn('flex flex-col space-y-1.5', className)} {...props} />
);
SheetHeader.displayName = 'SheetHeader';

const SheetTitle = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Title>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Title>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Title ref={ref} className={cn('text-lg font-semibold', className)} {...props} />
));
SheetTitle.displayName = 'SheetTitle';

const SheetDescription = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Description>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Description>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Description ref={ref} className={cn('text-sm text-muted-foreground', className)} {...props} />
));
SheetDescription.displayName = 'SheetDescription';

export {
  Sheet, SheetTrigger, SheetClose, SheetPortal, SheetOverlay,
  SheetContent, SheetHeader, SheetTitle, SheetDescription,
};
