import * as React from 'react';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { cn } from '@/shared/lib/utils';

const Dialog = DialogPrimitive.Root;
const DialogTrigger = DialogPrimitive.Trigger;
const DialogPortal = DialogPrimitive.Portal;
const DialogClose = DialogPrimitive.Close;

const DialogOverlay = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Overlay>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Overlay>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Overlay
    ref={ref}
    className={cn(
      'fixed inset-0 z-50 bg-black/60 backdrop-blur-[2px]',
      'data-[state=open]:animate-in data-[state=closed]:animate-out',
      'data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0',
      'data-[state=open]:duration-200 data-[state=closed]:duration-150',
      className,
    )}
    {...props}
  />
));
DialogOverlay.displayName = DialogPrimitive.Overlay.displayName;

const DialogContent = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content>
>(({ className, children, ...props }, ref) => (
  <DialogPortal>
    <DialogOverlay />
    <DialogPrimitive.Content
      ref={ref}
      className={cn(
        'surface-panel fixed left-1/2 top-1/2 z-50 flex w-[calc(100%-1.5rem)] max-w-lg',
        '-translate-x-1/2 -translate-y-1/2 flex-col border sm:rounded-lg',
        // The panel itself never scrolls - the inner wrapper below does. That
        // keeps the close button (and any sticky header/footer) anchored
        // instead of scrolling out of reach on a long form.
        'max-h-[90dvh] overflow-hidden',
        // The slide offsets cancel out the centring transform above; without
        // them the panel animates in from the top-left corner of the viewport.
        // ease-out on the way in, and a shorter ease-in on the way out, so
        // dismissing feels immediate rather than sluggish.
        'ease-out data-[state=open]:duration-200 data-[state=closed]:duration-150',
        'data-[state=open]:animate-in data-[state=closed]:animate-out',
        'data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0',
        'data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95',
        'data-[state=closed]:slide-out-to-left-1/2 data-[state=closed]:slide-out-to-top-[48%]',
        'data-[state=open]:slide-in-from-left-1/2 data-[state=open]:slide-in-from-top-[48%]',
        className,
      )}
      {...props}
    >
      {/* Padding steps down on small screens - a fixed p-6 costs 3rem of a
          360px-wide phone, which is where the long forms are hardest to use. */}
      <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto overscroll-contain p-4 sm:gap-4 sm:p-6">
        {children}
      </div>
      <DialogPrimitive.Close
        aria-label="Close dialog"
        className="absolute right-3 top-3 z-20 rounded-sm p-1 opacity-70 ring-offset-background transition-colors
                   hover:bg-destructive/10 hover:text-destructive hover:opacity-100
                   focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
      >
        <X className="h-4 w-4" />
        <span className="sr-only">Close</span>
      </DialogPrimitive.Close>
    </DialogPrimitive.Content>
  </DialogPortal>
));
DialogContent.displayName = DialogPrimitive.Content.displayName;

/**
 * Sticks to the top of the scrolling body, so the title of a long form stays
 * visible while the user scrolls it. The negative margins pull it out over
 * the body's own padding; `.surface-sticky-bar` (a near-solid, blurred
 * variant of the panel's own --card colour) is what actually occludes the
 * content passing underneath, in glass themes as well as flat ones.
 */
const DialogHeader = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
  <div
    className={cn(
      // The negative margins must track DialogContent's responsive padding
      // exactly, or the bar leaves an unpainted gutter at one breakpoint.
      'surface-sticky-bar sticky top-0 z-10 flex flex-col space-y-1.5 text-left',
      '-mx-4 -mt-4 px-4 pb-2.5 pt-4 sm:-mx-6 sm:-mt-6 sm:px-6 sm:pb-3 sm:pt-6',
      // Leave room for the close button so a long title never runs under it.
      'pr-12 sm:pr-12',
      className,
    )}
    {...props}
  />
);
DialogHeader.displayName = 'DialogHeader';

/**
 * Sticks to the bottom of the scrolling body. Before this, a form taller than
 * the viewport pushed Save and Cancel below the fold and the user had to
 * scroll to reach them - on a phone that read as "the dialog has no buttons".
 */
const DialogFooter = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
  <div
    className={cn(
      'surface-sticky-bar sticky bottom-0 z-10 mt-auto flex flex-col-reverse gap-2 border-t',
      '-mx-4 -mb-4 px-4 pb-4 pt-3 sm:-mx-6 sm:-mb-6 sm:flex-row sm:justify-end sm:px-6 sm:pb-6 sm:pt-4',
      className,
    )}
    {...props}
  />
);
DialogFooter.displayName = 'DialogFooter';

const DialogTitle = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Title>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Title>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Title ref={ref} className={cn('text-lg font-semibold', className)} {...props} />
));
DialogTitle.displayName = DialogPrimitive.Title.displayName;

const DialogDescription = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Description>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Description>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Description ref={ref} className={cn('text-sm text-muted-foreground', className)} {...props} />
));
DialogDescription.displayName = DialogPrimitive.Description.displayName;

export {
  Dialog, DialogPortal, DialogOverlay, DialogTrigger, DialogClose,
  DialogContent, DialogHeader, DialogFooter, DialogTitle, DialogDescription,
};
