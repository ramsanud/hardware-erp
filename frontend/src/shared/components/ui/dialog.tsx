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
        /*
         * CR-061: below sm this is a sheet rising from the bottom edge - the
         * shape every phone OS uses for this job, and it puts the form within
         * thumb reach instead of floating it mid-screen with a gutter on all
         * four sides.
         *
         * The animation is re-aimed one variable at a time rather than
         * overridden in index.css: tailwindcss-animate builds its keyframes
         * from the --tw-enter and --tw-exit variables, and the data-[state]
         * utilities above are attribute-qualified - so a plain stylesheet rule
         * loses to them on both specificity and source order. A max-sm variant
         * of the same utility is emitted after them and wins cleanly.
         */
        'max-sm:inset-x-0 max-sm:bottom-0 max-sm:top-auto max-sm:w-full max-sm:max-w-none',
        'max-sm:max-h-[92dvh] max-sm:translate-x-0 max-sm:translate-y-0',
        'max-sm:rounded-t-2xl max-sm:border-b-0',
        'max-sm:data-[state=open]:slide-in-from-left-0 max-sm:data-[state=closed]:slide-out-to-left-0',
        'max-sm:data-[state=open]:slide-in-from-bottom-[100%] max-sm:data-[state=closed]:slide-out-to-bottom-[100%]',
        'max-sm:data-[state=open]:zoom-in-100 max-sm:data-[state=closed]:zoom-out-100',
        className,
      )}
      {...props}
    >
      {/* Padding steps down on small screens - a fixed p-6 costs 3rem of a
          360px-wide phone, which is where the long forms are hardest to use. */}
      <div className="dialog-scroll flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto overscroll-contain p-4 sm:gap-4 sm:p-6">
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
 * visible while the user scrolls it. `.surface-sticky-bar` (a near-solid,
 * blurred variant of the panel's own --card colour) is what actually occludes
 * the content passing underneath, in glass themes as well as flat ones.
 *
 * BUG-FE-028: this bar reaches the panel's TOP edge because .dialog-scroll
 * drops its own top padding whenever it contains one of these (index.css) -
 * NOT via the negative top margin that used to be here. It is the same defect
 * BUG-FE-023 fixed at the bottom, arriving at the other end for the same
 * reason: `position: sticky; top: 0` pins to the scroll container's padding
 * edge, so with the container's pt-4 the bar came to rest 16px below the
 * visible top of the panel, and the form scrolled through the strip above it.
 * A negative margin cannot move a sticky element past that edge - it only
 * shifts where the bar starts before it sticks - so the padding itself has to
 * go, and the bar's own pt-4/sm:pt-6 supplies the inset instead.
 */
const DialogHeader = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
  <div
    className={cn(
      // The negative HORIZONTAL margins must track DialogContent's responsive
      // padding exactly, or the bar leaves an unpainted gutter at one
      // breakpoint. There is deliberately no negative top margin - see below.
      'dialog-sticky-header surface-sticky-bar sticky top-0 z-10 flex flex-col space-y-1.5 text-left',
      '-mx-4 px-4 pb-2.5 pt-4 sm:-mx-6 sm:px-6 sm:pb-3 sm:pt-6',
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
 *
 * BUG-FE-023: this bar reaches the panel's bottom edge because .dialog-scroll
 * drops its own bottom padding whenever it contains one of these (index.css) -
 * NOT via a negative margin. A sticky element cannot be pushed past its scroll
 * container's padding edge by margin at all, and the -mb-4 that used to be here
 * was in any case cancelled by the <form class="space-y-4"> almost every dialog
 * wraps its footer in. The visible symptom was a 16px strip of the scrolling
 * form showing below the pinned Save/Cancel row.
 */
const DialogFooter = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => (
  <div
    className={cn(
      'dialog-sticky-footer surface-sticky-bar sticky bottom-0 z-10 mt-auto flex flex-col-reverse gap-2 border-t',
      '-mx-4 px-4 pb-4 pt-3 sm:-mx-6 sm:flex-row sm:justify-end sm:px-6 sm:pb-6 sm:pt-4',
      // CR-061: on a phone this row sits on the screen's bottom edge, where
      // the home indicator would otherwise cross the last button.
      'max-sm:pb-[calc(1rem+env(safe-area-inset-bottom))]',
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
