/**
 * Where the API lives. Shared by both HTTP clients.
 *
 * Only the URL is shared: services/apiClient.ts and
 * services/platformAdminApiClient.ts keep separate axios instances and
 * separate interceptors on purpose, so a bug in one console's token handling
 * cannot bleed into the other. A base URL is not token handling, and keeping
 * two copies of this rule is what let the platform-admin client miss the
 * normalisation the tenant client got (BUG-FE-009).
 *
 * Unset - the intended configuration - means the relative '/api', so requests
 * are same-origin: the Vite dev proxy locally, the vercel.json rewrite in
 * production. That is not a convenience. The refresh token cookie is
 * SameSite=Strict, so a cross-origin XHR never sends it, and sign-in silently
 * stops persisting the moment the 15-minute access token expires.
 *
 * When it IS set, the '/api' suffix is added if missing. The server's
 * context-path is a fixed '/api' (application.yml), so a base URL without it
 * cannot be right - and getting it wrong produces '<host>/v1/auth/login', a
 * 404 whose CORS preflight failure looks like a CORS problem rather than a
 * path one. That cost a deploy on 2026-09-05.
 */
export function resolveApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL?.trim();
  if (!configured) {
    return '/api';
  }

  const trimmed = configured.replace(/\/+$/, '');
  const normalised = trimmed.endsWith('/api') ? trimmed : `${trimmed}/api`;

  // An absolute URL means cross-origin, which breaks the refresh cookie. Say
  // so once, loudly, rather than letting it surface days later as users being
  // logged out at seemingly random moments.
  if (/^https?:\/\//i.test(normalised)) {
    console.warn(
      `[apiClient] VITE_API_BASE_URL is set to an absolute URL (${normalised}). ` +
      'Requests will be cross-origin, and the SameSite=Strict refresh cookie will NOT be sent - ' +
      'sign-in will stop persisting once the access token expires. Prefer leaving ' +
      'VITE_API_BASE_URL unset and routing /api through the vercel.json rewrite (or the Vite dev proxy).',
    );
  }
  return normalised;
}
