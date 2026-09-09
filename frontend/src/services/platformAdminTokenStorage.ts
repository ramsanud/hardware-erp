/**
 * A completely separate in-memory store from tokenStorage.ts - see
 * platformAdminApiClient.ts for why the two clients are never allowed to
 * share state.
 *
 * CR-065 / BUG-SEC-006. Only the ACCESS token lives here. The refresh token
 * used to sit alongside it in this module, which meant any XSS on this origin
 * could read a 7-day credential for a console that can suspend tenants and
 * touch billing. It is now an HttpOnly, SameSite=Strict cookie the browser
 * attaches by itself (PlatformAdminRefreshTokenCookieService), so there is no
 * application state left for a script to read it out of - which is the point,
 * and why this module no longer exposes a getter for it.
 *
 * The old comment here said a page reload signing the admin out was
 * "accepted deliberately for Phase 1". That is no longer true either: the
 * cookie survives the reload, and PlatformAdminAuthProvider calls refresh on
 * mount to restore the session from it.
 */
let accessToken: string | null = null;

type Listener = (token: string | null) => void;
const listeners = new Set<Listener>();

export const platformAdminTokenStorage = {
  get(): string | null {
    return accessToken;
  },

  set(token: string | null): void {
    accessToken = token;
    listeners.forEach((listener) => listener(token));
  },

  clear(): void {
    this.set(null);
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
