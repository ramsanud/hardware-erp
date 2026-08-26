import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
} from 'react';
import type { ReactNode } from 'react';
import { useTheme } from './ThemeProvider';
import { readScoped, writeScoped } from './themeScope';
import { useThemeScope } from './useThemeScope';
import {
  COLOR_THEMES, DEFAULT_COLOR_THEME_ID, findColorTheme, type ColorTheme, type ColorThemeTokens,
} from './colorThemes';

const STORAGE_KEY = 'hardware-erp-color-theme';

/** One-time migration read: a pre-CR-034 install stored this unscoped. */
function readWithLegacyFallback(): string {
  return readScoped(STORAGE_KEY) ?? localStorage.getItem(STORAGE_KEY) ?? DEFAULT_COLOR_THEME_ID;
}

interface ColorThemeContextValue {
  colorThemeId: string;
  colorTheme: ColorTheme;
  setColorThemeId: (id: string) => void;
  themes: ColorTheme[];
}

const ColorThemeContext = createContext<ColorThemeContextValue | null>(null);

const TOKEN_TO_CSS_VAR: Record<keyof ColorThemeTokens, string> = {
  primary: '--primary',
  primaryForeground: '--primary-foreground',
  secondary: '--secondary',
  secondaryForeground: '--secondary-foreground',
  accent: '--accent',
  accentForeground: '--accent-foreground',
  ring: '--ring',
  sidebar: '--sidebar',
  sidebarForeground: '--sidebar-foreground',
  sidebarMuted: '--sidebar-muted',
  sidebarSection: '--sidebar-section',
  sidebarActive: '--sidebar-active',
  sidebarActiveBg: '--sidebar-active-bg',
  sidebarBorder: '--sidebar-border',
  sidebarHover: '--sidebar-hover',
  chart1: '--chart-1',
  chart2: '--chart-2',
  chart3: '--chart-3',
  chart4: '--chart-4',
  chart5: '--chart-5',
  heroGradient: '', // handled separately, not a plain CSS variable
};

/**
 * A second, independent axis alongside ThemeProvider's light/dark/system.
 * Applies a preset's tokens as inline custom properties on <html> - inline
 * style always wins over the stylesheet's :root/.dark rules, so this is a
 * clean override with no !important and no per-component change (every
 * component already reads hsl(var(--primary)) etc. via Tailwind's token
 * classes, CR-030-era work already established that pattern app-wide).
 * Re-applies whenever either the colour theme OR light/dark resolution
 * changes, since every preset carries its own light and dark variant.
 */
export function ColorThemeProvider({ children }: { children: ReactNode }) {
  const { resolvedTheme } = useTheme();
  const scope = useThemeScope();
  const [colorThemeId, setColorThemeIdState] = useState<string>(readWithLegacyFallback);

  // CR-034: re-read this scope's own stored preset whenever the signed-in user changes.
  useEffect(() => {
    setColorThemeIdState(readWithLegacyFallback());
  }, [scope]);

  const colorTheme = useMemo(() => findColorTheme(colorThemeId), [colorThemeId]);

  useEffect(() => {
    const tokens = resolvedTheme === 'dark' ? colorTheme.dark : colorTheme.light;
    const root = document.documentElement;
    (Object.keys(TOKEN_TO_CSS_VAR) as (keyof ColorThemeTokens)[]).forEach((key) => {
      const cssVar = TOKEN_TO_CSS_VAR[key];
      if (!cssVar) return;
      const value = tokens[key];
      if (typeof value === 'string') root.style.setProperty(cssVar, value);
    });
    root.dataset.colorTheme = colorTheme.id;
  }, [colorTheme, resolvedTheme]);

  const setColorThemeId = useCallback((id: string) => {
    writeScoped(STORAGE_KEY, id);
    setColorThemeIdState(id);
  }, []);

  const value = useMemo<ColorThemeContextValue>(
    () => ({
      colorThemeId, colorTheme, setColorThemeId, themes: COLOR_THEMES,
    }),
    [colorThemeId, colorTheme, setColorThemeId],
  );

  return <ColorThemeContext.Provider value={value}>{children}</ColorThemeContext.Provider>;
}

export function useColorTheme(): ColorThemeContextValue {
  const context = useContext(ColorThemeContext);
  if (!context) throw new Error('useColorTheme must be used inside ColorThemeProvider');
  return context;
}

/** The active preset's hero-gradient CSS (or a flat, restrained tint for the 7 themes that don't define one) - for login/dashboard-hero/profile-header surfaces only, never behind tables or forms. */
export function useHeroBackground(): string {
  const { colorTheme } = useColorTheme();
  const { resolvedTheme } = useTheme();
  const tokens = resolvedTheme === 'dark' ? colorTheme.dark : colorTheme.light;
  if (tokens.heroGradient) {
    return `linear-gradient(135deg, hsl(${tokens.heroGradient.from}), hsl(${tokens.heroGradient.to}))`;
  }
  return `hsl(${tokens.primary})`;
}
