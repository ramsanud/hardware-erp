import { useEffect } from 'react';
import { useTheme } from './ThemeProvider';
import { useColorTheme } from './ColorThemeProvider';

/**
 * CR-061. Keeps <meta name="theme-color"> in step with the resolved palette.
 *
 * On a phone the browser paints its own chrome - the status bar strip above
 * the page on Android, the toolbar background on iOS - and without this meta
 * it picks that colour itself. The result was an app whose header was Ocean
 * dark navy sitting under a stark white system bar: the eight colour themes
 * and the light/dark switch were invisible to the only part of a phone screen
 * the user cannot scroll away, which is what "the theme colours are not
 * adaptable on mobile" actually describes.
 *
 * The value is read back off the live <body> rather than recomputed from the
 * token tables, so it stays correct for whatever ColorThemeProvider and
 * DesignStyleProvider have just written - including any preset added later,
 * with no second place to update.
 *
 * The rAF matters: effects run children-first, so this component's effect
 * fires BEFORE its ColorThemeProvider parent has applied the new tokens.
 * Reading after the next frame is what makes the value the painted one
 * rather than the previous theme's.
 */
export function ThemeColorMeta() {
  const { resolvedTheme } = useTheme();
  const { colorThemeId } = useColorTheme();

  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      const painted = getComputedStyle(document.body).backgroundColor;
      if (!painted) return;

      /*
       * index.html ships two static theme-colour tags scoped with
       * media="(prefers-color-scheme: ...)" so the very first paint is not
       * white. Those are keyed to the OS preference, but the app's own
       * light/dark choice can disagree with it - so writing into one of them
       * would leave the other stale and let the browser pick the wrong one
       * (an app set to Light on a phone set to Dark, for instance). This owns
       * a single unscoped tag and retires the statics the first time it runs.
       */
      let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"][data-runtime]');
      if (!meta) {
        document.querySelectorAll('meta[name="theme-color"]').forEach((stale) => stale.remove());
        meta = document.createElement('meta');
        meta.name = 'theme-color';
        meta.dataset.runtime = '';
        document.head.appendChild(meta);
      }
      meta.content = painted;
    });
    return () => cancelAnimationFrame(frame);
  }, [resolvedTheme, colorThemeId]);

  return null;
}
