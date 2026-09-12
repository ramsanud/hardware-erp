import { cn } from '@/shared/lib/utils';

/**
 * CR-081. The Hardware ERP mark: a post-and-lintel H.
 *
 * Two posts carrying a beam that runs past them - the simplest structure in
 * building. The overhang is what makes it a mark rather than a letter, and
 * it is the detail that still reads at 16px, which is why the favicon is
 * this same path and not a simplified one.
 *
 * One component for every appearance - the auth hero, the sign-in card, the
 * sidebar when a shop has not uploaded its own logo - so the mark cannot
 * drift between them. The tile is `bg-primary`, so it follows the shop's
 * theme like everything else; the glyph is always the tile's foreground.
 *
 * Geometry on a 32 grid: posts 4.5 wide by 18 tall, beam 20 by 4.5, two
 * units of overhang each side, 1.6 radius on strokes and ~25% on the tile.
 */
interface BrandMarkProps {
  /** Tile size in px. The glyph scales with it. */
  size?: number;
  className?: string;
  title?: string;
}

/** The white H alone, for callers that draw their own tile. */
export function BrandGlyph({ size = 24, className }: { size?: number; className?: string }) {
  return (
    <svg
      viewBox="0 0 32 32"
      width={size}
      height={size}
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-hidden
    >
      <rect x="8" y="7" width="4.5" height="18" rx="1.6" fill="currentColor" />
      <rect x="19.5" y="7" width="4.5" height="18" rx="1.6" fill="currentColor" />
      <rect x="6" y="13.75" width="20" height="4.5" rx="1.6" fill="currentColor" />
    </svg>
  );
}

export function BrandMark({ size = 40, className, title = 'Hardware ERP' }: BrandMarkProps) {
  // Same ratios as the favicon and the design canvas - tile radius ~27%,
  // glyph ~60% - so the mark is one shape at every size it appears.
  return (
    <span
      role="img"
      aria-label={title}
      className={cn(
        'inline-flex shrink-0 items-center justify-center bg-primary text-primary-foreground',
        className,
      )}
      style={{ width: size, height: size, borderRadius: Math.round(size * 0.27) }}
    >
      <BrandGlyph size={Math.round(size * 0.6)} />
    </span>
  );
}
