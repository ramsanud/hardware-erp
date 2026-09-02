/**
 * A completely separate in-memory store from tokenStorage.ts - see
 * platformAdminApiClient.ts for why the two clients are never allowed to
 * share state. Both the access token and the refresh token live here only:
 * neither is ever written to localStorage or sessionStorage (hard rule 9
 * extended to the refresh token too, since the backend does not issue it as
 * an HttpOnly cookie for this console - see PlatformAdminAuthService).
 *
 * Consequence, accepted deliberately for Phase 1: a full page reload signs a
 * platform admin out. A later phase can add a cookie-backed refresh
 * transport the way the tenant side already has, if this proves annoying in
 * practice.
 */
let accessToken: string | null = null;
let refreshToken: string | null = null;

type Listener = (token: string | null) => void;
const listeners = new Set<Listener>();

export const platformAdminTokenStorage = {
  get(): string | null {
    return accessToken;
  },

  getRefreshToken(): string | null {
    return refreshToken;
  },

  set(token: string | null, refresh?: string | null): void {
    accessToken = token;
    if (refresh !== undefined) {
      refreshToken = refresh;
    }
    listeners.forEach((listener) => listener(token));
  },

  clear(): void {
    refreshToken = null;
    this.set(null);
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
