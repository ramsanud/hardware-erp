import { useId, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { motion, useReducedMotion } from 'motion/react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '@/shared/lib/utils';
import { Button } from '@/shared/components/ui/button';
import { Card, CardContent } from '@/shared/components/ui/card';
import { BrandGlyph } from '@/shared/components/BrandMark';

/**
 * CR-100. An "integrations" visual: six tiles wired to a centre mark by
 * animated traces, in a card with a title, a line of copy and one link.
 *
 * Adapted from the 21st.dev "integration-card" block. What changed, and why:
 *
 * - Tailwind v4 utilities (`bg-linear-to-b`, `aspect-564/460`, `max-w-141`,
 *   `h-13.5`, `ring-3`) rewritten for the v3.4 this project runs.
 * - `var(--color-primary)` / `var(--color-foreground)` became
 *   `hsl(var(--primary))` / `hsl(var(--foreground))` - this project's tokens
 *   are HSL triplets (index.css), and eleven themes resolve them.
 * - The block's own base-ui Button and its cva are gone; the project's
 *   Button (Radix Slot, `asChild`) renders the link. One button, one look.
 * - The Figma / Claude / React / Tailwind logos and the two CDN-hosted centre
 *   images are gone. The centre is the BrandMark; the tiles are whatever the
 *   caller passes - on the landing page, the channels and exports this
 *   product actually ships (rule 12: never claim what is not built).
 * - Honours `prefers-reduced-motion`: index.css neutralises CSS animation but
 *   not motion's JS-driven values, so the loops are switched off here.
 */

export interface IntegrationTile {
  id: string;
  /** Read by screen readers; drawn under the tile from `sm` up. */
  label: string;
  icon: LucideIcon;
}

/** Tile positions and the trace from the centre (282, 205) on a 564 x 410 canvas. */
const SLOTS = [
  { x: 110, y: 90, path: 'M 270 205 V 105 Q 270 90 255 90 H 110', delay: 0.1 },   // top-left
  { x: 360, y: 70, path: 'M 294 205 V 85 Q 294 70 309 70 H 360', delay: 0.2 },    // top-right
  { x: 160, y: 205, path: 'M 250 205 H 160', delay: 0.3 },                         // mid-left
  { x: 480, y: 205, path: 'M 314 205 H 480', delay: 0.4 },                         // mid-right
  { x: 282, y: 360, path: 'M 282 205 V 360', delay: 0.6 },                         // bottom-centre
  { x: 460, y: 340, path: 'M 314 215 V 325 Q 314 340 329 340 H 460', delay: 0.7 }, // bottom-right
] as const;

const CANVAS = { width: 564, height: 410 };

function AnimatedPath({ d, id, index, animate }: { d: string; id: string; index: number; animate: boolean }) {
  return (
    <>
      <path d={d} stroke="currentColor" strokeWidth="1" fill="none" className="text-border" />
      {animate ? (
        <motion.path
          d={d}
          stroke={`url(#${id})`}
          strokeWidth="2"
          fill="none"
          strokeDasharray="40 160"
          initial={{ strokeDashoffset: 200 }}
          animate={{ strokeDashoffset: -200 }}
          // Staggered by slot rather than Math.random(), so a re-render does not reshuffle the pulses.
          transition={{ duration: 4, repeat: Infinity, ease: 'linear', delay: index * 0.55 }}
        />
      ) : null}
      <defs>
        <linearGradient id={id} gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="transparent" />
          <stop offset="50%" stopColor="hsl(var(--primary))" stopOpacity="0.6" />
          <stop offset="100%" stopColor="transparent" />
        </linearGradient>
      </defs>
    </>
  );
}

export function Integration({ tiles }: { tiles: IntegrationTile[] }) {
  const containerId = useId();
  const reduced = useReducedMotion() ?? false;
  const placed = tiles.slice(0, SLOTS.length).map((tile, index) => ({ ...tile, ...SLOTS[index] }));

  return (
    <div className="relative h-full w-full" data-integration-visual>
      <svg
        className="pointer-events-none absolute inset-0 h-full w-full"
        viewBox={`0 0 ${CANVAS.width} ${CANVAS.height}`}
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden
      >
        {placed.map((tile, index) => (
          <AnimatedPath
            key={tile.id}
            d={tile.path}
            id={`${containerId}-${tile.id}`}
            index={index}
            animate={!reduced}
          />
        ))}
      </svg>

      {/* Centre: the product. */}
      <div className="absolute left-1/2 top-1/2 z-20 flex -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-lg border border-border bg-background p-0.5 shadow-md sm:rounded-2xl sm:p-2 sm:shadow-xl">
        <div className="rounded-lg border border-border p-1 sm:rounded-xl sm:p-2.5">
          {/* The BrandMark tile, sized by class rather than by its px prop so it can step up at sm. */}
          <span
            role="img"
            aria-label="Hardware ERP"
            className="flex h-5 w-5 items-center justify-center rounded-[27%] bg-primary text-primary-foreground sm:h-9 sm:w-9"
          >
            <BrandGlyph className="h-3 w-3 sm:h-[22px] sm:w-[22px]" />
          </span>
        </div>
        {!reduced ? (
          <motion.div
            aria-hidden
            className="absolute inset-0 rounded-lg border-2 border-primary/20 sm:rounded-2xl"
            animate={{ scale: [1, 1.15, 1], opacity: [0.4, 0, 0.4] }}
            transition={{ duration: 3, repeat: Infinity }}
          />
        ) : null}
      </div>

      {/* Tiles */}
      {placed.map((tile) => {
        const Icon = tile.icon;
        return (
          <motion.div
            key={tile.id}
            initial={reduced ? false : { opacity: 0, scale: 0.8 }}
            whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true }}
            transition={{ delay: tile.delay }}
            style={{ left: `${(tile.x / CANVAS.width) * 100}%`, top: `${(tile.y / CANVAS.height) * 100}%` }}
            className="absolute z-10 flex h-8 w-8 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-lg border border-border bg-background text-foreground shadow-sm sm:h-12 sm:w-12 sm:rounded-xl md:h-[54px] md:w-[54px]"
          >
            <Icon className="h-4 w-4 sm:h-6 sm:w-6" aria-hidden />
            <span className="absolute top-full mt-1.5 hidden whitespace-nowrap text-[11px] font-medium leading-none text-muted-foreground sm:block">
              {tile.label}
            </span>
            <span className="sr-only sm:hidden">{tile.label}</span>
          </motion.div>
        );
      })}
    </div>
  );
}

export function VisualContainer({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        'relative flex aspect-[564/460] w-full items-center justify-center overflow-hidden bg-muted p-8 sm:aspect-[564/410] dark:bg-muted/50',
        className,
      )}
    >
      {/* Dot grid - the foreground token at 20%, so it follows light/dark. */}
      <div
        aria-hidden
        className="absolute inset-0 opacity-20"
        style={{
          backgroundImage: 'radial-gradient(circle, hsl(var(--foreground)) 1px, transparent 1px)',
          backgroundSize: '32px 32px',
        }}
      />
      <div aria-hidden className="pointer-events-none absolute inset-0 bg-gradient-to-b from-background/60 via-transparent to-background/60" />
      <div className="relative z-10 flex h-full w-full items-center justify-center">{children}</div>
    </div>
  );
}

interface IntegrationCardProps {
  visual: ReactNode;
  title: string;
  description: string;
  /** In-app destination (a route or a `#section` anchor). */
  to: string;
  cta?: string;
  className?: string;
}

export function IntegrationCard({ visual, title, description, to, cta = 'Learn more', className }: IntegrationCardProps) {
  return (
    <Card className={cn('mx-auto flex w-full flex-col gap-0 overflow-hidden rounded-2xl border p-0 sm:max-w-[564px]', className)}>
      <VisualContainer>{visual}</VisualContainer>
      <CardContent className="flex flex-col gap-6 p-6 sm:gap-8 sm:p-8">
        <div className="flex flex-col gap-2">
          <h3 className="text-xl font-medium tracking-tight sm:text-2xl">{title}</h3>
          <p className="text-base leading-relaxed text-muted-foreground">{description}</p>
        </div>
        <Button asChild className="h-10 w-fit rounded-full px-5">
          {to.startsWith('#') ? <a href={to}>{cta}</a> : <Link to={to}>{cta}</Link>}
        </Button>
      </CardContent>
    </Card>
  );
}
