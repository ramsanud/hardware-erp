/**
 * Access token lives in a module variable, never localStorage or sessionStorage.
 *
 * An XSS bug can read any web storage. Keeping the token in memory means such a
 * bug steals a credential that dies in 15 minutes rather than one that survives
 * a browser restart. The refresh token is never visible to JavaScript at all -
 * the backend issues it as an HttpOnly, SameSite=Strict cookie scoped to
 * /api/v1/auth (see SECURITY_REGISTRY.md).
 *
 * Consequence, accepted deliberately: a full page reload loses the access
 * token. The app recovers by calling /auth/refresh on startup, which succeeds
 * because the cookie survives.
 */
let accessToken: string | null = null;

type Listener = (token: string | null) => void;
const listeners = new Set<Listener>();

export const tokenStorage = {
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
