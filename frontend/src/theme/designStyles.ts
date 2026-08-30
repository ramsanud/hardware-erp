/**
 * CR-034: the "design style" axis - a peer to ColorThemeProvider's colour
 * axis and ThemeProvider's light/dark axis. Where those two repaint the
 * app, this one changes how surfaces are built: flat vs. translucent vs.
 * soft-shadow vs. inflated. Each style computes real CSS values (never a
 * literal per-component visual variant) for a small, deliberate set of
 * generic tokens - --surface-bg-opacity, --surface-blur, --surface-border-
 * opacity, --surface-shadow, --control-shadow, --control-inset-shadow -
 * that the shared primitives (Card, Button, Input, DialogContent,
 * DropdownMenuContent - see index.css's .surface-panel/.control-surface)
 * already read. Because every page in this app is built from those same
 * shared primitives (Layout B, CR-012), giving the primitives real,
 * distinct token-driven treatments is what actually makes "the complete
 * application adapts" (spec §19) true, instead of hand-writing bespoke CSS
 * for dozens of page-level component categories that would drift out of
 * sync with each other within a month.
 *
 * Only background/shadow/blur tokens change per style. Text colour,
 * border presence needed for input legibility, and the focus ring
 * (--ring, untouched by any style) are never touched here - contrast is
 * preserved by construction, not by a separate accessibility pass bolted
 * on afterward (spec §8/§26). index.css also forces --surface-blur to 0
 * and --surface-bg-opacity to 1 under `prefers-contrast: more` regardless
 * of the chosen style, for the same reason.
 */

export type DesignStyleId =
  | 'minimal' | 'bento' | 'glass' | 'liquid-glass' | 'spatial' | 'neomorphism' | 'claymorphism';

export type Intensity = 'calm' | 'balanced' | 'expressive';
export type Elevation = 'flat' | 'subtle' | 'standard' | 'elevated';
export type CornerStyle = 'sharp' | 'compact' | 'standard' | 'rounded' | 'soft';
export type Motion = 'reduced' | 'standard' | 'expressive';

export interface SurfaceRecipe {
  /** 0-1, fill opacity of the card/dialog/dropdown background against --card. 1 = fully opaque (Minimalism default). */
  bgOpacity: number;
  /** px, backdrop-filter blur. 0 = no glass effect. */
  blurPx: number;
  /** 0-1, multiplies the default border colour's opacity - glass/neo/clay styles favour a near-invisible border in place of a hard line. */
  borderOpacity: number;
  /** Full box-shadow value (may be multi-layer/inset), already combined - never mixed with the literal string "none" as one layer among others. */
  shadow: string;
}

export interface ControlRecipe {
  shadow: string;
  /** Only neomorphism/claymorphism use this - an inset shadow pair for a "pressed" input. 'none' everywhere else. */
  insetShadow: string;
}

export interface DesignStyle {
  id: DesignStyleId;
  label: string;
  tagline: string;
  description: string;
  recommended?: boolean;
  surface: { light: SurfaceRecipe; dark: SurfaceRecipe };
  control: { light: ControlRecipe; dark: ControlRecipe };
}

const NONE_CONTROL: ControlRecipe = { shadow: 'none', insetShadow: 'none' };

export const DESIGN_STYLES: DesignStyle[] = [
  {
    id: 'minimal',
    label: 'Minimal',
    tagline: 'Recommended',
    description: 'Clean flat surfaces, restrained shadows, maximum data density. The default for daily ERP work.',
    recommended: true,
    surface: {
      light: { bgOpacity: 1, blurPx: 0, borderOpacity: 1, shadow: '0 1px 2px 0 rgb(15 23 42 / 0.05)' },
      dark: { bgOpacity: 1, blurPx: 0, borderOpacity: 1, shadow: '0 1px 2px 0 rgb(0 0 0 / 0.35)' },
    },
    control: { light: NONE_CONTROL, dark: NONE_CONTROL },
  },
  {
    id: 'bento',
    label: 'Bento',
    tagline: 'Dashboard',
    description: 'Asymmetric, information-dense card grid for the dashboard and KPI summaries. Everything else stays Minimal.',
    surface: {
      light: { bgOpacity: 1, blurPx: 0, borderOpacity: 1, shadow: '0 1px 3px 0 rgb(15 23 42 / 0.07), 0 1px 2px -1px rgb(15 23 42 / 0.05)' },
      dark: { bgOpacity: 1, blurPx: 0, borderOpacity: 1, shadow: '0 1px 3px 0 rgb(0 0 0 / 0.4), 0 1px 2px -1px rgb(0 0 0 / 0.3)' },
    },
    control: { light: NONE_CONTROL, dark: NONE_CONTROL },
  },
  {
    id: 'glass',
    label: 'Glass',
    tagline: 'Depth',
    description: 'Translucent, softly blurred surfaces on dropdowns, panels and modals. Tables and forms stay fully opaque and readable.',
    surface: {
      light: { bgOpacity: 0.72, blurPx: 14, borderOpacity: 0.5, shadow: '0 8px 32px -8px rgb(15 23 42 / 0.18)' },
      dark: { bgOpacity: 0.55, blurPx: 14, borderOpacity: 0.4, shadow: '0 8px 32px -8px rgb(0 0 0 / 0.55)' },
    },
    control: {
      light: { shadow: '0 1px 2px 0 rgb(15 23 42 / 0.06)', insetShadow: 'none' },
      dark: { shadow: '0 1px 2px 0 rgb(0 0 0 / 0.4)', insetShadow: 'none' },
    },
  },
  {
    id: 'liquid-glass',
    label: 'Liquid Glass',
    tagline: 'Premium',
    description: 'Layered translucency with a soft top highlight and colour-tinted glow. Reserved for auth, profile header and dashboard hero.',
    surface: {
      light: {
        bgOpacity: 0.66, blurPx: 20, borderOpacity: 0.4,
        shadow: 'inset 0 1px 0 0 rgb(255 255 255 / 0.45), 0 12px 40px -12px rgb(37 99 235 / 0.28)',
      },
      dark: {
        bgOpacity: 0.5, blurPx: 20, borderOpacity: 0.3,
        shadow: 'inset 0 1px 0 0 rgb(255 255 255 / 0.08), 0 12px 40px -12px rgb(0 0 0 / 0.65)',
      },
    },
    control: {
      light: { shadow: 'inset 0 1px 0 0 rgb(255 255 255 / 0.3), 0 2px 6px -1px rgb(15 23 42 / 0.12)', insetShadow: 'none' },
      dark: { shadow: 'inset 0 1px 0 0 rgb(255 255 255 / 0.06), 0 2px 6px -1px rgb(0 0 0 / 0.45)', insetShadow: 'none' },
    },
  },
  {
    id: 'spatial',
    label: 'Spatial',
    tagline: 'Advanced',
    description: 'Opaque surfaces with deliberately deeper elevation, so key cards read as lifted off the page. Dense tables and forms stay flat.',
    surface: {
      light: { bgOpacity: 1, blurPx: 0, borderOpacity: 1, shadow: '0 1px 2px 0 rgb(15 23 42 / 0.04), 0 16px 28px -10px rgb(15 23 42 / 0.16)' },
      dark: { bgOpacity: 1, blurPx: 0, borderOpacity: 1, shadow: '0 1px 2px 0 rgb(0 0 0 / 0.3), 0 16px 28px -10px rgb(0 0 0 / 0.55)' },
    },
    control: {
      light: { shadow: '0 1px 2px 0 rgb(15 23 42 / 0.06)', insetShadow: 'none' },
      dark: { shadow: '0 1px 2px 0 rgb(0 0 0 / 0.35)', insetShadow: 'none' },
    },
  },
  {
    id: 'neomorphism',
    label: 'Neomorphic',
    tagline: 'Optional',
    description: 'Soft raised surfaces with a dual highlight/shadow pair, pressed-in inputs. A personalisation option, not the default.',
    surface: {
      light: { bgOpacity: 1, blurPx: 0, borderOpacity: 0, shadow: '7px 7px 16px rgb(15 23 42 / 0.09), -7px -7px 16px rgb(255 255 255 / 0.75)' },
      dark: { bgOpacity: 1, blurPx: 0, borderOpacity: 0, shadow: '7px 7px 16px rgb(0 0 0 / 0.55), -7px -7px 16px rgb(255 255 255 / 0.03)' },
    },
    control: {
      light: { shadow: '4px 4px 10px rgb(15 23 42 / 0.10), -4px -4px 10px rgb(255 255 255 / 0.7)', insetShadow: 'inset 3px 3px 7px rgb(15 23 42 / 0.09), inset -3px -3px 7px rgb(255 255 255 / 0.6)' },
      dark: { shadow: '4px 4px 10px rgb(0 0 0 / 0.5), -4px -4px 10px rgb(255 255 255 / 0.025)', insetShadow: 'inset 3px 3px 7px rgb(0 0 0 / 0.45), inset -3px -3px 7px rgb(255 255 255 / 0.02)' },
    },
  },
  {
    id: 'claymorphism',
    label: 'Claymorphic',
    tagline: 'Optional',
    description: 'Soft, gently inflated components with chunky rounded shadows. Enterprise-restrained, not a cartoon UI.',
    surface: {
      light: { bgOpacity: 1, blurPx: 0, borderOpacity: 0, shadow: '0 12px 22px -6px rgb(15 23 42 / 0.16), inset 0 1px 0 0 rgb(255 255 255 / 0.55)' },
      dark: { bgOpacity: 1, blurPx: 0, borderOpacity: 0, shadow: '0 12px 22px -6px rgb(0 0 0 / 0.55), inset 0 1px 0 0 rgb(255 255 255 / 0.06)' },
    },
    control: {
      light: { shadow: '0 7px 16px -4px rgb(15 23 42 / 0.20)', insetShadow: 'none' },
      dark: { shadow: '0 7px 16px -4px rgb(0 0 0 / 0.55)', insetShadow: 'none' },
    },
  },
];

export const DEFAULT_DESIGN_STYLE_ID: DesignStyleId = 'minimal';

export function findDesignStyle(id: string): DesignStyle {
  return DESIGN_STYLES.find((s) => s.id === id) ?? DESIGN_STYLES[0];
}

/**
 * Scales a box-shadow string's alpha channel(s) by `scale`, used for the
 * Elevation dial (flat/subtle/standard/elevated). Splits on top-level
 * commas (safe here - every recipe above uses modern space-separated
 * rgb() syntax, never a comma inside the colour function itself) so each
 * shadow *layer* can be judged on its own, rather than trying to protect
 * "the highlight" by an alpha-value guess - Minimal's own base shadow is
 * already close to as faint as Liquid Glass's highlight line, so a
 * threshold on alpha alone silently exempted the wrong layers (caught
 * live: Elevation=Flat visibly did nothing under Minimal until this was
 * rewritten to detect the liquid-glass sheen line structurally instead).
 */
export function scaleShadowAlpha(shadow: string, scale: number): string {
  if (shadow === 'none') return 'none';
  if (scale === 0) return 'none';
  if (scale === 1) return shadow;
  const HIGHLIGHT_LAYER = /^inset\s+0\s+1px\s+0\s+0\s+rgb\(255 255 255/;
  return shadow
    .split(',')
    .map((rawLayer) => {
      const layer = rawLayer.trim();
      if (HIGHLIGHT_LAYER.test(layer)) return layer; // preserve the sheen line's own alpha untouched
      return layer.replace(/\/\s*([\d.]+)\)/, (_match, alpha: string) => {
        const value = Number(alpha);
        const scaled = Math.min(0.95, Math.max(0.02, value * scale));
        return `/ ${scaled.toFixed(2)})`;
      });
    })
    .join(', ');
}

export const INTENSITY_BLUR_SCALE: Record<Intensity, number> = { calm: 0.55, balanced: 1, expressive: 1.5 };
export const ELEVATION_SHADOW_SCALE: Record<Elevation, number> = { flat: 0, subtle: 0.55, standard: 1, elevated: 1.7 };
export const CORNER_RADIUS_REM: Record<CornerStyle, string> = {
  sharp: '0rem', compact: '0.25rem', standard: '0.5rem', rounded: '0.75rem', soft: '1.1rem',
};
export const MOTION_DURATION_MS: Record<Motion, number> = { reduced: 0, standard: 150, expressive: 260 };

// ---------------------------------------------------------------------------
// Reading comfort (CR-046). Two axes the shop owner controls directly, kept
// on the same appearance record as the design style rather than in a second
// store, so one save writes one object.
// ---------------------------------------------------------------------------

export type FontScale = 'compact' | 'standard' | 'large' | 'xlarge';

/**
 * Applied as the root font-size. Everything in the UI is sized in rem, so
 * moving this one value scales the whole interface proportionally instead of
 * only the body copy - which is what a shop owner reading a counter screen at
 * arm's length actually needs.
 */
export const FONT_SCALE_PX: Record<FontScale, string> = {
  compact: '15px', standard: '16px', large: '17.5px', xlarge: '19px',
};

export type FontFamilyId = 'system' | 'indic' | 'serif' | 'mono';

/**
 * Font stacks only - no webfont is downloaded, so this costs nothing at load
 * and works offline.
 *
 * `indic` exists because this ERP is used in Tamil Nadu and a shop types
 * customer and product names in Tamil, Hindi or Malayalam. The default UI
 * stack has no Indic coverage on some systems and those names render as
 * tofu boxes. Nirmala UI ships with Windows and covers Devanagari, Tamil,
 * Telugu, Kannada, Malayalam, Gujarati, Bengali, Odia, Gurmukhi; Noto Sans
 * is the Android/Linux equivalent. This makes Indic text READABLE - it is
 * not translation of the interface, which is a separate piece of work.
 */
export const FONT_FAMILY_STACK: Record<FontFamilyId, string> = {
  system:
    'ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
  indic:
    '"Segoe UI", "Nirmala UI", "Noto Sans", "Noto Sans Tamil", "Noto Sans Devanagari", '
    + '"Noto Sans Malayalam", "Noto Sans Telugu", "Noto Sans Kannada", Roboto, ui-sans-serif, system-ui, sans-serif',
  serif:
    'ui-serif, Georgia, Cambria, "Times New Roman", "Noto Serif", serif',
  mono:
    'ui-monospace, "Cascadia Mono", "Segoe UI Mono", "Roboto Mono", Menlo, Consolas, monospace',
};

export const FONT_SCALE_OPTIONS: { value: FontScale; label: string }[] = [
  { value: 'compact', label: 'Compact' },
  { value: 'standard', label: 'Standard' },
  { value: 'large', label: 'Large' },
  { value: 'xlarge', label: 'Extra large' },
];

export const FONT_FAMILY_OPTIONS: { value: FontFamilyId; label: string; hint: string }[] = [
  { value: 'system', label: 'System', hint: 'Matches your device' },
  { value: 'indic', label: 'Indian languages', hint: 'Tamil, Hindi, Malayalam and more' },
  { value: 'serif', label: 'Serif', hint: 'Traditional, print-like' },
  { value: 'mono', label: 'Monospace', hint: 'Fixed width' },
];
