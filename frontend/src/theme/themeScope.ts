/**
 * CR-034: every theme preference (mode, colour, design style, intensity,
 * corner, elevation, motion) is stored client-side in localStorage, which
 * is shared by every tenant that ever signs in on this browser. Without
 * scoping, shop A's owner picking "Liquid Glass + Ocean + Dark" would leak
 * straight into shop B's session the next time someone logs into shop B on
 * the same machine - exactly what the spec's "must be isolated per shop/
 * tenant" rule forbids. `mobile_no`/`email` are globally unique across
 * tenants (CR-016), so a user id is already an unambiguous per-tenant scope
 * with zero backend change needed - two different tenants can never share
 * a user id.
 *
 * A plain module-level store (not React state) because every theme
 * provider needs to read the current scope synchronously during its very
 * first render, before AuthProvider has resolved who's signed in - the
 * scope starts as "guest" and AuthProvider calls setThemeScope() the
 * moment a user is known, which notifies subscribers so each provider can
 * re-read its own key under the new scope.
 */

const SCOPE_STORAGE_KEY = 'hardware-erp-theme-scope';

type ScopeListener = (scope: string) => void;

const listeners = new Set<ScopeListener>();

function readStoredScope(): string {
  try {
    return localStorage.getItem(SCOPE_STORAGE_KEY) ?? 'guest';
  } catch {
    return 'guest';
  }
}

let currentScope = readStoredScope();

export function getThemeScope(): string {
  return currentScope;
}

/** Call with the signed-in user's id on login/refresh, and null on logout. */
export function setThemeScope(userId: number | string | null): void {
  const next = userId === null || userId === undefined ? 'guest' : String(userId);
  if (next === currentScope) return;
  currentScope = next;
  try {
    localStorage.setItem(SCOPE_STORAGE_KEY, next);
  } catch {
    // Private-browsing/storage-full - the scope still updates in memory for this tab.
  }
  listeners.forEach((listener) => listener(next));
}

export function subscribeThemeScope(listener: ScopeListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function scopedStorageKey(base: string): string {
  return `${base}:${currentScope}`;
}

export function readScoped(base: string): string | null {
  try {
    return localStorage.getItem(scopedStorageKey(base));
  } catch {
    return null;
  }
}

export function writeScoped(base: string, value: string): void {
  try {
    localStorage.setItem(scopedStorageKey(base), value);
  } catch {
    // Ignore - the in-memory value for this tab still applies.
  }
}
