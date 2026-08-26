import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { readScoped, writeScoped } from './themeScope';
import { useThemeScope } from './useThemeScope';

export type Theme = 'light' | 'dark' | 'system';

interface ThemeContextValue {
  theme: Theme;
  resolvedTheme: 'light' | 'dark';
  setTheme: (theme: Theme) => void;
}

const STORAGE_KEY = 'hardware-erp-theme';
const ThemeContext = createContext<ThemeContextValue | null>(null);

function systemPrefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

/** One-time migration read: a pre-CR-034 install stored this unscoped. Only consulted for the very first "guest" read, never once a real scoped value exists. */
function readWithLegacyFallback(): Theme | null {
  return (readScoped(STORAGE_KEY) as Theme | null) ?? (localStorage.getItem(STORAGE_KEY) as Theme | null);
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const scope = useThemeScope();
  const [theme, setThemeState] = useState<Theme>(() => readWithLegacyFallback() ?? 'system');

  // CR-034: re-read this scope's own stored mode whenever the signed-in user changes.
  useEffect(() => {
    setThemeState(readWithLegacyFallback() ?? 'system');
  }, [scope]);
  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>('light');

  useEffect(() => {
    const root = document.documentElement;

    const apply = () => {
      const next = theme === 'system' ? (systemPrefersDark() ? 'dark' : 'light') : theme;
      root.classList.toggle('dark', next === 'dark');
      setResolvedTheme(next);
    };

    apply();

    // Only follow the OS while the user has chosen "system".
    if (theme !== 'system') return;
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    media.addEventListener('change', apply);
    return () => media.removeEventListener('change', apply);
  }, [theme]);

  const setTheme = useCallback((next: Theme) => {
    writeScoped(STORAGE_KEY, next);
    setThemeState(next);
  }, []);

  const value = useMemo(
    () => ({ theme, resolvedTheme, setTheme }),
    [theme, resolvedTheme, setTheme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used inside ThemeProvider');
  return context;
}
