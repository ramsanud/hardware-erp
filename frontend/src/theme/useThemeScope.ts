import { useEffect, useState } from 'react';
import { getThemeScope, subscribeThemeScope } from './themeScope';

/** Re-renders whenever setThemeScope() runs (login/logout/user switch) so a provider can re-read its scoped key. */
export function useThemeScope(): string {
  const [scope, setScope] = useState(getThemeScope);
  useEffect(() => subscribeThemeScope(setScope), []);
  return scope;
}
