import {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
} from 'react';
import type { ReactNode } from 'react';
import { brandService } from '@/modules/settings/services/brandService';
import type { SubscriptionTier } from '@/modules/settings/types';

interface AppChromeContextValue {
  brandName: string | null;
  hasLogo: boolean;
  /** Bumped on every logo upload/remove so useAuthenticatedImage re-fetches even when hasLogo stays true (a new image replacing an old one). */
  logoVersion: number;
  /** Bumped on every avatar upload/remove, consumed anywhere the signed-in user's photo is shown (header, Profile). */
  avatarVersion: number;
  /** Null until the first fetch resolves - treat as "unknown yet", not as FREE. */
  subscriptionTier: SubscriptionTier | null;
  /** Called by ShopSettingsPage after a save or logo change, so the Sidebar reflects it without a page reload. */
  refreshBrand: () => Promise<void>;
  bumpAvatarVersion: () => void;
}

const AppChromeContext = createContext<AppChromeContextValue | null>(null);

/**
 * The Sidebar brand and header avatar were each fetching their own data once
 * on mount, so a Settings/Profile save elsewhere in the still-mounted
 * AppLayout never showed up until a full page reload. This context is the
 * single source both read from and both writers (ShopSettingsPage,
 * ProfilePage) push into.
 */
export function AppChromeProvider({ children }: { children: ReactNode }) {
  const [brandName, setBrandName] = useState<string | null>(null);
  const [hasLogo, setHasLogo] = useState(false);
  const [logoVersion, setLogoVersion] = useState(0);
  const [avatarVersion, setAvatarVersion] = useState(0);
  const [subscriptionTier, setSubscriptionTier] = useState<SubscriptionTier | null>(null);

  const refreshBrand = useCallback(async () => {
    try {
      const brand = await brandService.get();
      setBrandName(brand.name);
      setHasLogo(brand.hasLogo);
      setSubscriptionTier(brand.subscriptionTier);
    } catch {
      // falls back to the static APP_NAME shown by SidebarBrand
    }
    setLogoVersion((v) => v + 1);
  }, []);

  useEffect(() => { void refreshBrand(); }, [refreshBrand]);

  const bumpAvatarVersion = useCallback(() => setAvatarVersion((v) => v + 1), []);

  const value = useMemo<AppChromeContextValue>(
    () => ({
      brandName, hasLogo, logoVersion, avatarVersion, subscriptionTier, refreshBrand, bumpAvatarVersion,
    }),
    [brandName, hasLogo, logoVersion, avatarVersion, subscriptionTier, refreshBrand, bumpAvatarVersion],
  );

  return <AppChromeContext.Provider value={value}>{children}</AppChromeContext.Provider>;
}

export function useAppChrome(): AppChromeContextValue {
  const context = useContext(AppChromeContext);
  if (!context) throw new Error('useAppChrome must be used inside AppChromeProvider');
  return context;
}
