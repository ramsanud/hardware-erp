import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
} from 'react';
import type { ReactNode } from 'react';
import { useTheme } from './ThemeProvider';
import { readScoped, writeScoped } from './themeScope';
import { useThemeScope } from './useThemeScope';
import {
  CORNER_RADIUS_REM, DEFAULT_DESIGN_STYLE_ID, DESIGN_STYLES, ELEVATION_SHADOW_SCALE,
  INTENSITY_BLUR_SCALE, MOTION_DURATION_MS, findDesignStyle, scaleShadowAlpha,
  type CornerStyle, type DesignStyleId, type Elevation, type Intensity, type Motion,
} from './designStyles';

const STORAGE_KEY = 'hardware-erp-appearance';

interface AppearanceSettings {
  designStyleId: DesignStyleId;
  intensity: Intensity;
  corner: CornerStyle;
  elevation: Elevation;
  motion: Motion;
}

const DEFAULTS: AppearanceSettings = {
  designStyleId: DEFAULT_DESIGN_STYLE_ID,
  intensity: 'balanced',
  corner: 'standard',
  elevation: 'standard',
  motion: 'standard',
};

interface DesignStyleContextValue extends AppearanceSettings {
  styles: typeof DESIGN_STYLES;
  setDesignStyleId: (id: DesignStyleId) => void;
  setIntensity: (v: Intensity) => void;
  setCorner: (v: CornerStyle) => void;
  setElevation: (v: Elevation) => void;
  setMotion: (v: Motion) => void;
  /** True when the OS-level "reduce motion" preference is forcing Motion=Reduced regardless of the stored choice. */
  motionForcedByOs: boolean;
}

const DesignStyleContext = createContext<DesignStyleContextValue | null>(null);

function readSettings(): AppearanceSettings {
  const raw = readScoped(STORAGE_KEY);
  if (!raw) return DEFAULTS;
  try {
    const parsed = JSON.parse(raw) as Partial<AppearanceSettings>;
    return { ...DEFAULTS, ...parsed };
  } catch {
    return DEFAULTS;
  }
}

function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * CR-034: the "how surfaces are built" axis, alongside ThemeProvider
 * (light/dark) and ColorThemeProvider (colour). Computes final CSS custom
 * property VALUES in JS (same pattern ColorThemeProvider already uses
 * successfully) rather than relying on CSS calc() chains across shadow/
 * blur/opacity, which don't compose reliably across browsers - intensity
 * and elevation are applied as numeric scale factors to each design
 * style's base recipe here, then written once as plain strings.
 */
export function DesignStyleProvider({ children }: { children: ReactNode }) {
  const { resolvedTheme } = useTheme();
  const scope = useThemeScope();
  const [settings, setSettings] = useState<AppearanceSettings>(readSettings);
  const [motionForcedByOs, setMotionForcedByOs] = useState(prefersReducedMotion);

  // CR-034: re-read this scope's own stored preferences whenever the signed-in user changes.
  useEffect(() => {
    setSettings(readSettings());
  }, [scope]);

  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const apply = () => setMotionForcedByOs(media.matches);
    apply();
    media.addEventListener('change', apply);
    return () => media.removeEventListener('change', apply);
  }, []);

  const effectiveMotion: Motion = motionForcedByOs ? 'reduced' : settings.motion;

  useEffect(() => {
    const root = document.documentElement;
    const style = findDesignStyle(settings.designStyleId);
    const surface = resolvedTheme === 'dark' ? style.surface.dark : style.surface.light;
    const control = resolvedTheme === 'dark' ? style.control.dark : style.control.light;
    const blurScale = INTENSITY_BLUR_SCALE[settings.intensity];
    const shadowScale = ELEVATION_SHADOW_SCALE[settings.elevation];

    root.style.setProperty('--surface-bg-opacity', String(surface.bgOpacity));
    root.style.setProperty('--surface-blur', `${(surface.blurPx * blurScale).toFixed(1)}px`);
    root.style.setProperty('--surface-border-opacity', String(surface.borderOpacity));
    root.style.setProperty('--surface-shadow', scaleShadowAlpha(surface.shadow, shadowScale));
    root.style.setProperty('--control-shadow', scaleShadowAlpha(control.shadow, shadowScale));
    root.style.setProperty('--control-inset-shadow', control.insetShadow);
    root.style.setProperty('--radius', CORNER_RADIUS_REM[settings.corner]);
    root.style.setProperty('--motion-duration', `${MOTION_DURATION_MS[effectiveMotion]}ms`);

    root.dataset.designStyle = style.id;
    root.dataset.intensity = settings.intensity;
    root.dataset.elevation = settings.elevation;
    root.dataset.motion = effectiveMotion;
  }, [settings, resolvedTheme, effectiveMotion]);

  const patch = useCallback((partial: Partial<AppearanceSettings>) => {
    setSettings((current) => {
      const next = { ...current, ...partial };
      writeScoped(STORAGE_KEY, JSON.stringify(next));
      return next;
    });
  }, []);

  const setDesignStyleId = useCallback((id: DesignStyleId) => patch({ designStyleId: id }), [patch]);
  const setIntensity = useCallback((v: Intensity) => patch({ intensity: v }), [patch]);
  const setCorner = useCallback((v: CornerStyle) => patch({ corner: v }), [patch]);
  const setElevation = useCallback((v: Elevation) => patch({ elevation: v }), [patch]);
  const setMotion = useCallback((v: Motion) => patch({ motion: v }), [patch]);

  const value = useMemo<DesignStyleContextValue>(() => ({
    ...settings,
    styles: DESIGN_STYLES,
    setDesignStyleId,
    setIntensity,
    setCorner,
    setElevation,
    setMotion,
    motionForcedByOs,
  }), [settings, setDesignStyleId, setIntensity, setCorner, setElevation, setMotion, motionForcedByOs]);

  return <DesignStyleContext.Provider value={value}>{children}</DesignStyleContext.Provider>;
}

export function useDesignStyle(): DesignStyleContextValue {
  const context = useContext(DesignStyleContext);
  if (!context) throw new Error('useDesignStyle must be used inside DesignStyleProvider');
  return context;
}
