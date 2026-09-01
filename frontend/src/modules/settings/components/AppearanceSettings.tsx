import { Check, Laptop, Moon, RotateCcw, Sun } from 'lucide-react';
import { Card, CardContent } from '@/shared/components/ui/card';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Badge } from '@/shared/components/ui/badge';
import { cn } from '@/shared/lib/utils';
import { useTheme, type Theme } from '@/theme/ThemeProvider';
import { useColorTheme } from '@/theme/ColorThemeProvider';
import { useDesignStyle, DEFAULT_APPEARANCE_SETTINGS } from '@/theme/DesignStyleProvider';
import { DEFAULT_COLOR_THEME_ID } from '@/theme/colorThemes';
import {
  CORNER_RADIUS_REM, type CornerStyle, type Elevation, type Intensity, type Motion,
} from '@/theme/designStyles';
import { FONT_FAMILY_OPTIONS, FONT_SCALE_OPTIONS } from '@/theme/designStyles';

const MODE_OPTIONS: { value: Theme; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'System', icon: Laptop },
];

const INTENSITY_OPTIONS: { value: Intensity; label: string; hint: string }[] = [
  { value: 'calm', label: 'Calm', hint: 'Minimal shadows, blur and motion' },
  { value: 'balanced', label: 'Balanced', hint: 'Standard modern SaaS look' },
  { value: 'expressive', label: 'Expressive', hint: 'More depth, blur and motion' },
];

const CORNER_OPTIONS: { value: CornerStyle; label: string }[] = [
  { value: 'sharp', label: 'Sharp' },
  { value: 'compact', label: 'Compact' },
  { value: 'standard', label: 'Standard' },
  { value: 'rounded', label: 'Rounded' },
  { value: 'soft', label: 'Soft' },
];

const ELEVATION_OPTIONS: { value: Elevation; label: string }[] = [
  { value: 'flat', label: 'Flat' },
  { value: 'subtle', label: 'Subtle' },
  { value: 'standard', label: 'Standard' },
  { value: 'elevated', label: 'Elevated' },
];

const MOTION_OPTIONS: { value: Motion; label: string }[] = [
  { value: 'reduced', label: 'Reduced' },
  { value: 'standard', label: 'Standard' },
  { value: 'expressive', label: 'Expressive' },
];

function SegmentedControl<T extends string>({
  value, options, onChange, ariaLabel,
}: { value: T; options: { value: T; label: string }[]; onChange: (v: T) => void; ariaLabel: string }) {
  return (
    <div className="inline-flex flex-wrap rounded-lg border p-1" role="group" aria-label={ariaLabel}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          aria-pressed={value === option.value}
          className={cn(
            'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
            value === option.value ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

/**
 * CR-034: the full Theme & Appearance experience - Design Style (7 real
 * paradigms, theme/designStyles.ts), Colour Theme (11 presets), Mode,
 * Intensity, Corner, Elevation and Motion, plus a live preview panel built
 * from the app's own real Card/Button/Input/Badge components (not a mock
 * image - what you see here is what every page actually looks like, since
 * every one of those components already reads the same tokens this page
 * writes). Used both as a standalone page (AppearancePage, linked from
 * Shop Settings) and embedded as a Profile tab - one implementation, two
 * entry points, matching the spec's "My Profile -> Appearance OR
 * Administration -> Shop Settings -> Appearance" requirement without
 * maintaining the controls twice.
 */
export function AppearanceSettings() {
  const { theme, setTheme } = useTheme();
  const { colorThemeId, setColorThemeId, themes } = useColorTheme();
  const {
    designStyleId, intensity, corner, elevation, motion, motionForcedByOs,
    fontScale, fontFamily,
    styles, setDesignStyleId, setIntensity, setCorner, setElevation, setMotion,
    setFontScale, setFontFamily,
  } = useDesignStyle();

  const resetToDefault = () => {
    setTheme('system');
    setColorThemeId(DEFAULT_COLOR_THEME_ID);
    setDesignStyleId(DEFAULT_APPEARANCE_SETTINGS.designStyleId);
    setIntensity(DEFAULT_APPEARANCE_SETTINGS.intensity);
    setCorner(DEFAULT_APPEARANCE_SETTINGS.corner);
    setElevation(DEFAULT_APPEARANCE_SETTINGS.elevation);
    setMotion(DEFAULT_APPEARANCE_SETTINGS.motion);
    setFontScale(DEFAULT_APPEARANCE_SETTINGS.fontScale);
    setFontFamily(DEFAULT_APPEARANCE_SETTINGS.fontFamily);
  };

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          Every choice below applies immediately and is remembered for you on this device.
        </p>
        <Button type="button" variant="outline" size="sm" onClick={resetToDefault}>
          <RotateCcw className="h-3.5 w-3.5" />
          Reset to default theme
        </Button>
      </div>

      <section>
        <p className="mb-1 text-sm font-medium">Design style</p>
        <p className="mb-3 text-xs text-muted-foreground">
          Changes how surfaces are built - flat, translucent, soft-shadow or inflated. Applies immediately across the whole app.
        </p>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {styles.map((style) => {
            const active = style.id === designStyleId;
            const preview = style.surface.light;
            const controlPreview = style.control.light;
            return (
              <button
                key={style.id}
                type="button"
                onClick={() => setDesignStyleId(style.id)}
                aria-pressed={active}
                className={cn(
                  'flex flex-col gap-3 rounded-lg border p-3 text-left transition-all hover:border-primary/50',
                  active ? 'border-primary ring-2 ring-primary/30' : 'border-border',
                )}
              >
                <div
                  className="flex h-16 items-center gap-2 rounded-md border p-2"
                  style={{
                    background: 'linear-gradient(135deg, hsl(var(--muted)), hsl(var(--secondary)))',
                  }}
                >
                  <div
                    className="h-full w-8 shrink-0 rounded"
                    style={{
                      background: `hsl(var(--card) / ${preview.bgOpacity})`,
                      backdropFilter: `blur(${preview.blurPx}px)`,
                      boxShadow: preview.shadow,
                      borderColor: `hsl(var(--border) / ${preview.borderOpacity})`,
                      borderWidth: 1,
                    }}
                  />
                  <div
                    className="h-6 flex-1 rounded"
                    style={{
                      background: 'hsl(var(--primary))',
                      boxShadow: controlPreview.shadow,
                    }}
                  />
                </div>
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <span className="flex items-center gap-1.5 text-sm font-medium">
                      {style.label}
                      {style.recommended ? <Badge variant="success" className="text-[10px]">Recommended</Badge> : null}
                    </span>
                    <span className="mt-0.5 block text-xs text-muted-foreground">{style.description}</span>
                  </div>
                  {active ? <Check className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden /> : null}
                </div>
              </button>
            );
          })}
        </div>
      </section>

      <section>
        <p className="mb-2 text-sm font-medium">Colour theme</p>
        <div className="grid grid-cols-3 gap-2 sm:grid-cols-5">
          {themes.map((t) => {
            const active = t.id === colorThemeId;
            return (
              <button
                key={t.id}
                type="button"
                onClick={() => setColorThemeId(t.id)}
                aria-pressed={active}
                title={t.description}
                className={cn(
                  'flex flex-col items-start gap-2 rounded-lg border p-3 text-left transition-all',
                  'hover:border-primary/50 hover:shadow-sm',
                  active ? 'border-primary ring-2 ring-primary/30' : 'border-border',
                )}
              >
                <span className="flex w-full items-center justify-between">
                  <span className="h-6 w-6 rounded-full border border-black/5" style={{ background: `hsl(${t.swatch})` }} aria-hidden />
                  {active ? <Check className="h-4 w-4 text-primary" aria-hidden /> : null}
                </span>
                <span className="text-sm font-medium">{t.label}</span>
              </button>
            );
          })}
        </div>
      </section>

      <section className="grid gap-6 sm:grid-cols-2">
        <div>
          <p className="mb-2 text-sm font-medium">Mode</p>
          <SegmentedControl ariaLabel="Appearance mode" value={theme} options={MODE_OPTIONS} onChange={setTheme} />
        </div>
        <div>
          <p className="mb-2 text-sm font-medium">Visual intensity</p>
          <SegmentedControl ariaLabel="Visual intensity" value={intensity} options={INTENSITY_OPTIONS} onChange={setIntensity} />
          <p className="mt-1 text-xs text-muted-foreground">{INTENSITY_OPTIONS.find((o) => o.value === intensity)?.hint}</p>
        </div>
        <div>
          <p className="mb-2 text-sm font-medium">Corner style</p>
          <SegmentedControl ariaLabel="Corner style" value={corner} options={CORNER_OPTIONS} onChange={setCorner} />
        </div>
        <div>
          <p className="mb-2 text-sm font-medium">Elevation</p>
          <SegmentedControl ariaLabel="Elevation" value={elevation} options={ELEVATION_OPTIONS} onChange={setElevation} />
        </div>
        <div>
          <p className="mb-2 text-sm font-medium">Motion</p>
          <SegmentedControl ariaLabel="Motion" value={motion} options={MOTION_OPTIONS} onChange={setMotion} />
          {motionForcedByOs ? (
            <p className="mt-1 text-xs text-muted-foreground">
              Your system is set to reduce motion, so animations stay off regardless of this choice.
            </p>
          ) : null}
        </div>
      </section>

      <section>
        <h3 className="mb-1 text-sm font-semibold">Reading</h3>
        <p className="mb-3 text-xs text-muted-foreground">
          Text size scales the whole interface, not just the words.
        </p>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <p className="mb-2 text-sm font-medium">Text size</p>
            <SegmentedControl ariaLabel="Text size" value={fontScale}
                              options={FONT_SCALE_OPTIONS} onChange={setFontScale} />
          </div>
          <div>
            <p className="mb-2 text-sm font-medium">Font</p>
            <SegmentedControl ariaLabel="Font" value={fontFamily}
                              options={FONT_FAMILY_OPTIONS} onChange={setFontFamily} />
            <p className="mt-1 text-xs text-muted-foreground">
              {FONT_FAMILY_OPTIONS.find((o) => o.value === fontFamily)?.hint}
            </p>
          </div>
        </div>
      </section>

      <section>
        <p className="mb-2 text-sm font-medium">Preview</p>
        <Card>
          <CardContent className="flex flex-wrap items-center gap-4 p-5">
            <div className="flex h-16 w-24 shrink-0 flex-col justify-between rounded-md bg-sidebar p-2" style={{ borderRadius: CORNER_RADIUS_REM[corner] }}>
              <div className="h-2 w-10 rounded-full bg-sidebar-active-bg" />
              <div className="h-2 w-14 rounded-full bg-sidebar-hover" />
              <div className="h-2 w-8 rounded-full bg-sidebar-hover" />
            </div>
            <Button size="sm">Save</Button>
            <Button size="sm" variant="outline">Cancel</Button>
            <Input placeholder="Search…" className="h-9 w-32" readOnly />
            <Badge>Active</Badge>
            <Badge variant="warning">Low stock</Badge>
            <div className="flex items-end gap-1" aria-hidden>
              {[6, 10, 7, 13, 9].map((height, index) => (
                <span key={index} className="w-2 rounded-sm bg-chart-1" style={{ height: height * 2 }} />
              ))}
            </div>
          </CardContent>
        </Card>
      </section>
    </div>
  );
}
