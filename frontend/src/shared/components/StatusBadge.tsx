import { Badge, type BadgeProps } from '@/shared/components/ui/badge';
import { cn } from '@/shared/lib/utils';

/**
 * The one status pill in the application.
 *
 * Two things it fixes over calling <Badge> directly at every site:
 *
 * 1. Inactive used to render `secondary` - the same neutral grey as a role
 *    name or a count chip. On a price list scanned at a shop counter, "this
 *    product is switched off" has to be readable at a glance and grey is not;
 *    it is now the destructive red, against the success green of Active.
 *
 * 2. The dot. Colour alone is never the signal - the label is always present
 *    and is what a screen reader and a red-green colourblind user actually
 *    read - but the dot gives the colour a saturated anchor next to muted
 *    text, which is what makes the state legible in peripheral vision.
 */
export interface StatusBadgeProps extends Omit<BadgeProps, 'children'> {
  label: string;
  tone: 'positive' | 'negative' | 'critical' | 'neutral' | 'pending';
}

const TONE: Record<StatusBadgeProps['tone'], NonNullable<BadgeProps['variant']>> = {
  positive: 'success',
  negative: 'destructive',
  critical: 'destructive-solid',
  neutral: 'secondary',
  pending: 'warning',
};

export function StatusBadge({ label, tone, className, ...props }: StatusBadgeProps) {
  return (
    <Badge variant={TONE[tone]} className={cn('gap-1.5', className)} {...props}>
      <span
        aria-hidden
        className={cn(
          'h-1.5 w-1.5 shrink-0 rounded-full',
          // currentColor on the tinted variants, an explicit light dot on the
          // solid one where currentColor is already the badge's own text.
          tone === 'critical' ? 'bg-destructive-foreground' : 'bg-current',
        )}
      />
      {label}
    </Badge>
  );
}
