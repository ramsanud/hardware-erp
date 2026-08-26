/**
 * The 8 named colour presets. Each is a genuinely different visual
 * personality, not just a swapped --primary - sidebar tint, chart ramp
 * and ring colour all move together so a theme reads as one coherent
 * choice, not a single repainted button. Every value is HSL "H S% L%",
 * the exact format index.css's :root/.dark blocks already use, so a
 * preset is applied by writing these same custom properties inline on
 * <html> (see ColorThemeProvider) - no new CSS mechanism, just a second
 * axis alongside the existing light/dark ThemeProvider.
 *
 * Only the tokens that meaningfully change per theme are listed here;
 * everything else (background, card, border, destructive, success,
 * warning, radius) stays the shared base from index.css so status colours
 * never get reinterpreted per theme and every theme still passes the same
 * contrast bar.
 */
export interface ColorThemeTokens {
  primary: string;
  primaryForeground: string;
  secondary: string;
  secondaryForeground: string;
  accent: string;
  accentForeground: string;
  ring: string;
  sidebar: string;
  sidebarForeground: string;
  sidebarMuted: string;
  sidebarSection: string;
  sidebarActive: string;
  sidebarActiveBg: string;
  sidebarBorder: string;
  sidebarHover: string;
  chart1: string;
  chart2: string;
  chart3: string;
  chart4: string;
  chart5: string;
  /** Only Aurora uses these - an atmospheric two-stop gradient for hero surfaces (login, dashboard greeting, profile header). Every other theme leaves this null and those surfaces fall back to a flat, restrained tint (CR-033 §23: gradients belong on a few surfaces, never behind tables). */
  heroGradient: { from: string; to: string } | null;
}

export interface ColorTheme {
  id: string;
  label: string;
  description: string;
  /** Shown as a small swatch on the picker - the theme's defining hue at a mid lightness, independent of light/dark. */
  swatch: string;
  light: ColorThemeTokens;
  dark: ColorThemeTokens;
}

export const COLOR_THEMES: ColorTheme[] = [
  {
    id: 'royal-blue',
    label: 'Royal Blue',
    description: 'The original, professional ERP palette.',
    swatch: '217 91% 45%',
    light: {
      primary: '217 91% 35%', primaryForeground: '210 40% 98%',
      secondary: '210 40% 96%', secondaryForeground: '222 47% 11%',
      accent: '210 40% 96%', accentForeground: '222 47% 11%',
      ring: '217 91% 35%',
      sidebar: '222 47% 11%', sidebarForeground: '210 40% 96%', sidebarMuted: '215 20% 62%',
      sidebarSection: '215 16% 47%', sidebarActive: '217 91% 60%', sidebarActiveBg: '217 60% 20%',
      sidebarBorder: '217 33% 18%', sidebarHover: '217 33% 16%',
      chart1: '217 91% 55%', chart2: '142 66% 42%', chart3: '262 70% 60%', chart4: '32 90% 52%', chart5: '215 16% 60%',
      heroGradient: null,
    },
    dark: {
      primary: '217 91% 60%', primaryForeground: '222 47% 11%',
      secondary: '217 33% 18%', secondaryForeground: '210 40% 96%',
      accent: '217 33% 18%', accentForeground: '210 40% 96%',
      ring: '217 91% 60%',
      sidebar: '222 47% 7%', sidebarForeground: '210 40% 96%', sidebarMuted: '215 20% 60%',
      sidebarSection: '215 16% 45%', sidebarActive: '217 91% 65%', sidebarActiveBg: '217 60% 18%',
      sidebarBorder: '217 33% 14%', sidebarHover: '217 33% 13%',
      chart1: '217 91% 62%', chart2: '142 60% 50%', chart3: '262 70% 68%', chart4: '32 90% 58%', chart5: '215 20% 65%',
      heroGradient: null,
    },
  },
  {
    id: 'indigo',
    label: 'Indigo',
    description: 'A modern SaaS look with a violet-leaning primary.',
    swatch: '243 75% 55%',
    light: {
      primary: '243 75% 47%', primaryForeground: '210 40% 98%',
      secondary: '240 30% 96%', secondaryForeground: '243 40% 15%',
      accent: '240 30% 96%', accentForeground: '243 40% 15%',
      ring: '243 75% 47%',
      sidebar: '243 40% 10%', sidebarForeground: '235 30% 95%', sidebarMuted: '235 18% 62%',
      sidebarSection: '235 14% 48%', sidebarActive: '243 85% 68%', sidebarActiveBg: '243 55% 22%',
      sidebarBorder: '243 30% 18%', sidebarHover: '243 30% 16%',
      chart1: '243 80% 62%', chart2: '199 89% 48%', chart3: '292 60% 60%', chart4: '32 90% 52%', chart5: '235 14% 60%',
      heroGradient: null,
    },
    dark: {
      primary: '243 85% 68%', primaryForeground: '243 40% 12%',
      secondary: '243 30% 18%', secondaryForeground: '235 30% 95%',
      accent: '243 30% 18%', accentForeground: '235 30% 95%',
      ring: '243 85% 68%',
      sidebar: '243 40% 7%', sidebarForeground: '235 30% 95%', sidebarMuted: '235 18% 60%',
      sidebarSection: '235 14% 45%', sidebarActive: '243 90% 74%', sidebarActiveBg: '243 55% 20%',
      sidebarBorder: '243 30% 15%', sidebarHover: '243 30% 14%',
      chart1: '243 85% 70%', chart2: '199 89% 55%', chart3: '292 65% 66%', chart4: '32 90% 58%', chart5: '235 18% 65%',
      heroGradient: null,
    },
  },
  {
    id: 'emerald',
    label: 'Emerald',
    description: 'Fresh and business-like - suits inventory-heavy workflows.',
    swatch: '152 60% 40%',
    light: {
      primary: '152 69% 28%', primaryForeground: '150 40% 98%',
      secondary: '150 30% 95%', secondaryForeground: '152 40% 13%',
      accent: '150 30% 95%', accentForeground: '152 40% 13%',
      ring: '152 69% 28%',
      sidebar: '160 35% 9%', sidebarForeground: '150 30% 95%', sidebarMuted: '150 16% 60%',
      sidebarSection: '150 14% 46%', sidebarActive: '152 70% 58%', sidebarActiveBg: '152 50% 18%',
      sidebarBorder: '155 28% 16%', sidebarHover: '155 28% 14%',
      chart1: '152 66% 42%', chart2: '199 89% 48%', chart3: '38 92% 50%', chart4: '262 70% 60%', chart5: '150 14% 58%',
      heroGradient: null,
    },
    dark: {
      primary: '152 60% 48%', primaryForeground: '152 40% 10%',
      secondary: '155 28% 16%', secondaryForeground: '150 30% 95%',
      accent: '155 28% 16%', accentForeground: '150 30% 95%',
      ring: '152 60% 48%',
      sidebar: '160 35% 6%', sidebarForeground: '150 30% 95%', sidebarMuted: '150 16% 58%',
      sidebarSection: '150 14% 44%', sidebarActive: '152 65% 62%', sidebarActiveBg: '152 50% 16%',
      sidebarBorder: '155 28% 13%', sidebarHover: '155 28% 12%',
      chart1: '152 60% 50%', chart2: '199 89% 55%', chart3: '38 92% 56%', chart4: '262 70% 68%', chart5: '150 16% 62%',
      heroGradient: null,
    },
  },
  {
    id: 'slate',
    label: 'Slate',
    description: 'Restrained, low-saturation enterprise tone.',
    swatch: '215 20% 45%',
    light: {
      primary: '215 28% 26%', primaryForeground: '210 20% 98%',
      secondary: '210 16% 95%', secondaryForeground: '215 25% 15%',
      accent: '210 16% 95%', accentForeground: '215 25% 15%',
      ring: '215 28% 26%',
      sidebar: '215 25% 12%', sidebarForeground: '210 16% 92%', sidebarMuted: '212 12% 62%',
      sidebarSection: '212 10% 48%', sidebarActive: '199 60% 62%', sidebarActiveBg: '215 22% 22%',
      sidebarBorder: '215 18% 20%', sidebarHover: '215 18% 18%',
      chart1: '215 30% 45%', chart2: '199 55% 45%', chart3: '32 40% 50%', chart4: '152 30% 40%', chart5: '212 10% 58%',
      heroGradient: null,
    },
    dark: {
      primary: '210 25% 70%', primaryForeground: '215 25% 12%',
      secondary: '215 18% 20%', secondaryForeground: '210 16% 92%',
      accent: '215 18% 20%', accentForeground: '210 16% 92%',
      ring: '210 25% 70%',
      sidebar: '215 25% 8%', sidebarForeground: '210 16% 92%', sidebarMuted: '212 12% 58%',
      sidebarSection: '212 10% 45%', sidebarActive: '199 65% 68%', sidebarActiveBg: '215 22% 18%',
      sidebarBorder: '215 18% 16%', sidebarHover: '215 18% 15%',
      chart1: '210 25% 68%', chart2: '199 55% 55%', chart3: '32 40% 58%', chart4: '152 30% 50%', chart5: '212 12% 62%',
      heroGradient: null,
    },
  },
  {
    id: 'amber',
    label: 'Amber',
    description: 'Warm, hardware-shop tone without losing readability.',
    swatch: '38 92% 50%',
    light: {
      primary: '30 88% 38%', primaryForeground: '30 40% 98%',
      secondary: '32 40% 95%', secondaryForeground: '25 45% 15%',
      accent: '32 40% 95%', accentForeground: '25 45% 15%',
      ring: '30 88% 38%',
      sidebar: '25 35% 10%', sidebarForeground: '32 30% 94%', sidebarMuted: '30 16% 62%',
      sidebarSection: '30 14% 48%', sidebarActive: '38 92% 60%', sidebarActiveBg: '30 55% 20%',
      sidebarBorder: '28 28% 17%', sidebarHover: '28 28% 15%',
      chart1: '38 92% 50%', chart2: '199 89% 48%', chart3: '152 66% 42%', chart4: '0 72% 50%', chart5: '30 14% 58%',
      heroGradient: null,
    },
    dark: {
      primary: '38 92% 58%', primaryForeground: '25 45% 12%',
      secondary: '28 28% 17%', secondaryForeground: '32 30% 94%',
      accent: '28 28% 17%', accentForeground: '32 30% 94%',
      ring: '38 92% 58%',
      sidebar: '25 35% 7%', sidebarForeground: '32 30% 94%', sidebarMuted: '30 16% 60%',
      sidebarSection: '30 14% 45%', sidebarActive: '38 92% 64%', sidebarActiveBg: '30 55% 18%',
      sidebarBorder: '28 28% 14%', sidebarHover: '28 28% 13%',
      chart1: '38 92% 58%', chart2: '199 89% 55%', chart3: '152 60% 50%', chart4: '0 63% 58%', chart5: '30 16% 62%',
      heroGradient: null,
    },
  },
  {
    id: 'ocean',
    label: 'Ocean',
    description: 'Crisp blue-cyan, modern and cool.',
    swatch: '199 89% 48%',
    light: {
      primary: '199 89% 34%', primaryForeground: '199 40% 98%',
      secondary: '196 40% 95%', secondaryForeground: '199 45% 14%',
      accent: '196 40% 95%', accentForeground: '199 45% 14%',
      ring: '199 89% 34%',
      sidebar: '202 45% 10%', sidebarForeground: '196 30% 95%', sidebarMuted: '196 16% 62%',
      sidebarSection: '196 14% 48%', sidebarActive: '191 91% 62%', sidebarActiveBg: '199 55% 20%',
      sidebarBorder: '200 32% 17%', sidebarHover: '200 32% 15%',
      chart1: '199 89% 48%', chart2: '152 66% 42%', chart3: '243 75% 60%', chart4: '32 90% 52%', chart5: '196 14% 58%',
      heroGradient: null,
    },
    dark: {
      primary: '191 91% 62%', primaryForeground: '199 45% 10%',
      secondary: '200 32% 17%', secondaryForeground: '196 30% 95%',
      accent: '200 32% 17%', accentForeground: '196 30% 95%',
      ring: '191 91% 62%',
      sidebar: '202 45% 6%', sidebarForeground: '196 30% 95%', sidebarMuted: '196 16% 60%',
      sidebarSection: '196 14% 45%', sidebarActive: '191 95% 68%', sidebarActiveBg: '199 55% 17%',
      sidebarBorder: '200 32% 14%', sidebarHover: '200 32% 13%',
      chart1: '199 89% 55%', chart2: '152 60% 50%', chart3: '243 80% 68%', chart4: '32 90% 58%', chart5: '196 16% 62%',
      heroGradient: null,
    },
  },
  {
    id: 'teal',
    label: 'Teal',
    description: 'Cool blue-green, calm and legible for long sessions.',
    swatch: '173 80% 30%',
    light: {
      primary: '173 80% 26%', primaryForeground: '173 40% 98%',
      secondary: '170 30% 95%', secondaryForeground: '173 45% 13%',
      accent: '170 30% 95%', accentForeground: '173 45% 13%',
      ring: '173 80% 26%',
      sidebar: '178 40% 9%', sidebarForeground: '170 30% 95%', sidebarMuted: '170 16% 60%',
      sidebarSection: '170 14% 46%', sidebarActive: '173 80% 55%', sidebarActiveBg: '173 55% 18%',
      sidebarBorder: '176 30% 16%', sidebarHover: '176 30% 14%',
      chart1: '173 70% 38%', chart2: '199 89% 48%', chart3: '38 92% 50%', chart4: '262 70% 60%', chart5: '170 14% 58%',
      heroGradient: null,
    },
    dark: {
      primary: '173 65% 48%', primaryForeground: '173 45% 10%',
      secondary: '176 30% 16%', secondaryForeground: '170 30% 95%',
      accent: '176 30% 16%', accentForeground: '170 30% 95%',
      ring: '173 65% 48%',
      sidebar: '178 40% 6%', sidebarForeground: '170 30% 95%', sidebarMuted: '170 16% 58%',
      sidebarSection: '170 14% 44%', sidebarActive: '173 75% 60%', sidebarActiveBg: '173 55% 16%',
      sidebarBorder: '176 30% 13%', sidebarHover: '176 30% 12%',
      chart1: '173 65% 50%', chart2: '199 89% 55%', chart3: '38 92% 56%', chart4: '262 70% 68%', chart5: '170 16% 62%',
      heroGradient: null,
    },
  },
  {
    id: 'violet',
    label: 'Violet',
    description: 'Bold, distinctive purple - stands out in a browser full of blue tabs.',
    swatch: '280 65% 55%',
    light: {
      primary: '280 65% 45%', primaryForeground: '280 40% 98%',
      secondary: '280 30% 96%', secondaryForeground: '280 40% 15%',
      accent: '280 30% 96%', accentForeground: '280 40% 15%',
      ring: '280 65% 45%',
      sidebar: '280 40% 10%', sidebarForeground: '280 25% 95%', sidebarMuted: '278 18% 62%',
      sidebarSection: '278 14% 48%', sidebarActive: '280 80% 70%', sidebarActiveBg: '280 50% 22%',
      sidebarBorder: '280 30% 18%', sidebarHover: '280 30% 16%',
      chart1: '280 65% 58%', chart2: '199 89% 48%', chart3: '330 65% 58%', chart4: '38 92% 50%', chart5: '278 14% 58%',
      heroGradient: null,
    },
    dark: {
      primary: '280 80% 72%', primaryForeground: '280 40% 12%',
      secondary: '280 30% 18%', secondaryForeground: '280 25% 95%',
      accent: '280 30% 18%', accentForeground: '280 25% 95%',
      ring: '280 80% 72%',
      sidebar: '280 40% 7%', sidebarForeground: '280 25% 95%', sidebarMuted: '278 18% 60%',
      sidebarSection: '278 14% 45%', sidebarActive: '280 85% 76%', sidebarActiveBg: '280 50% 19%',
      sidebarBorder: '280 30% 15%', sidebarHover: '280 30% 14%',
      chart1: '280 80% 74%', chart2: '199 89% 55%', chart3: '330 65% 64%', chart4: '38 92% 58%', chart5: '278 18% 62%',
      heroGradient: null,
    },
  },
  {
    id: 'rose',
    label: 'Rose',
    description: 'Warm rose-pink accent, softer than a pure alert red.',
    swatch: '340 75% 50%',
    light: {
      primary: '340 75% 42%', primaryForeground: '340 40% 98%',
      secondary: '340 35% 96%', secondaryForeground: '340 45% 16%',
      accent: '340 35% 96%', accentForeground: '340 45% 16%',
      ring: '340 75% 42%',
      sidebar: '340 40% 10%', sidebarForeground: '340 25% 95%', sidebarMuted: '338 18% 62%',
      sidebarSection: '338 14% 48%', sidebarActive: '340 85% 68%', sidebarActiveBg: '340 55% 21%',
      sidebarBorder: '340 30% 18%', sidebarHover: '340 30% 16%',
      chart1: '340 75% 52%', chart2: '199 89% 48%', chart3: '38 92% 50%', chart4: '262 70% 60%', chart5: '338 14% 58%',
      heroGradient: null,
    },
    dark: {
      primary: '340 85% 70%', primaryForeground: '340 45% 12%',
      secondary: '340 30% 18%', secondaryForeground: '340 25% 95%',
      accent: '340 30% 18%', accentForeground: '340 25% 95%',
      ring: '340 85% 70%',
      sidebar: '340 40% 7%', sidebarForeground: '340 25% 95%', sidebarMuted: '338 18% 60%',
      sidebarSection: '338 14% 45%', sidebarActive: '340 90% 74%', sidebarActiveBg: '340 55% 18%',
      sidebarBorder: '340 30% 15%', sidebarHover: '340 30% 14%',
      chart1: '340 85% 72%', chart2: '199 89% 55%', chart3: '38 92% 56%', chart4: '262 70% 68%', chart5: '338 18% 62%',
      heroGradient: null,
    },
  },
  {
    id: 'minimal',
    label: 'Monochrome',
    description: 'Near-monochrome - lets your data supply the colour.',
    swatch: '222 15% 35%',
    light: {
      primary: '222 20% 18%', primaryForeground: '210 20% 98%',
      secondary: '210 15% 95%', secondaryForeground: '222 20% 15%',
      accent: '210 15% 95%', accentForeground: '222 20% 15%',
      ring: '222 20% 18%',
      sidebar: '222 15% 13%', sidebarForeground: '210 12% 92%', sidebarMuted: '215 10% 62%',
      sidebarSection: '215 8% 48%', sidebarActive: '210 15% 88%', sidebarActiveBg: '222 12% 24%',
      sidebarBorder: '218 12% 20%', sidebarHover: '218 12% 18%',
      chart1: '222 20% 35%', chart2: '199 40% 45%', chart3: '32 30% 48%', chart4: '152 25% 40%', chart5: '215 8% 60%',
      heroGradient: null,
    },
    dark: {
      primary: '210 15% 85%', primaryForeground: '222 20% 12%',
      secondary: '218 12% 20%', secondaryForeground: '210 12% 92%',
      accent: '218 12% 20%', accentForeground: '210 12% 92%',
      ring: '210 15% 85%',
      sidebar: '222 15% 9%', sidebarForeground: '210 12% 92%', sidebarMuted: '215 10% 58%',
      sidebarSection: '215 8% 45%', sidebarActive: '210 15% 92%', sidebarActiveBg: '222 12% 20%',
      sidebarBorder: '218 12% 16%', sidebarHover: '218 12% 15%',
      chart1: '222 15% 60%', chart2: '199 40% 55%', chart3: '32 30% 58%', chart4: '152 25% 50%', chart5: '215 10% 65%',
      heroGradient: null,
    },
  },
  {
    id: 'aurora',
    label: 'Aurora',
    description: 'A premium violet-blue accent with subtle gradient surfaces on login, dashboard and profile.',
    swatch: '262 70% 60%',
    light: {
      primary: '258 70% 48%', primaryForeground: '260 40% 98%',
      secondary: '258 35% 96%', secondaryForeground: '260 40% 15%',
      accent: '258 35% 96%', accentForeground: '260 40% 15%',
      ring: '258 70% 48%',
      sidebar: '258 40% 10%', sidebarForeground: '260 30% 95%', sidebarMuted: '258 18% 64%',
      sidebarSection: '258 14% 50%', sidebarActive: '270 85% 72%', sidebarActiveBg: '260 55% 22%',
      sidebarBorder: '260 30% 18%', sidebarHover: '260 30% 16%',
      chart1: '258 75% 60%', chart2: '199 89% 52%', chart3: '320 65% 62%', chart4: '32 90% 55%', chart5: '258 14% 60%',
      heroGradient: { from: '258 70% 48%', to: '199 89% 52%' },
    },
    dark: {
      primary: '270 85% 72%', primaryForeground: '260 40% 12%',
      secondary: '260 30% 18%', secondaryForeground: '260 30% 95%',
      accent: '260 30% 18%', accentForeground: '260 30% 95%',
      ring: '270 85% 72%',
      sidebar: '258 40% 7%', sidebarForeground: '260 30% 95%', sidebarMuted: '258 18% 62%',
      sidebarSection: '258 14% 47%', sidebarActive: '272 90% 78%', sidebarActiveBg: '260 55% 20%',
      sidebarBorder: '260 30% 15%', sidebarHover: '260 30% 14%',
      chart1: '270 85% 74%', chart2: '199 89% 58%', chart3: '320 70% 68%', chart4: '32 90% 60%', chart5: '258 18% 65%',
      heroGradient: { from: '270 60% 30%', to: '199 70% 28%' },
    },
  },
];

export const DEFAULT_COLOR_THEME_ID = 'royal-blue';

export function findColorTheme(id: string): ColorTheme {
  return COLOR_THEMES.find((t) => t.id === id) ?? COLOR_THEMES[0];
}
