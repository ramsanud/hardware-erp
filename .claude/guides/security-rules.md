# Security rules

Load this when: touching auth, sessions, permissions, tenant scoping,
anything that logs, uploads, sends, or reads another shop's data.

## Hard rules

5. **No self-registration endpoint.** The owner creates accounts (CR-008).
   Shop registration (`/v1/tenants/register`) creates a shop *and its
   owner*; that is the only public write.
6. **Authorization is permission-based.** Never `hasRole('OWNER')` for a
   business rule. Every endpoint carries `@PreAuthorize` on a
   `PermissionCode`; the frontend mirror in `auth/constants` only hides UI.
   `DEVELOPER_INSPECT` is the one code no default role holds, OWNER included.
7. **Users and suppliers are soft-deleted.** Financial records reference
   them forever.
8. **Security events → `security_audit_log`. Business changes →
   `activity_log`** (CR-015). Sign-ins, failed attempts, resets and
   permission changes are security; everything with a before/after value is
   business.
9. **Access token in memory only.** Never `localStorage`. The refresh token
   is an HttpOnly cookie; reuse of a refresh token is detected and the family
   revoked (BUG-AUTH-014 made sure that path is tested).

## Tenant isolation

- The tenant comes from the JWT via `SecurityUtils.requireCurrentTenantId()`
  — **never** from a request parameter, path variable or body.
- Every repository read on a tenant-owned table filters by it
  (`findByIdAndTenantId`). A shop-wide query that forgot the filter would
  serve every tenant's rows to any permission holder; CR-070 refused to add
  the activity-log viewer until V55 added the column, for exactly that reason.
- Client-side per-user state is scoped by user id (`themeScope`), and user
  ids are unique across tenants, so a shared browser cannot leak one shop's
  preferences into another's session.

## Sign-in and second factor

- Password → second factor (CR-058). `mfaToken` is a challenge, not a
  session; reaching `/login` clears any pending one (BUG-FE-025). MFA can be
  switched off server-wide (CR-060), in which case the password response
  carries a finished session.
- Rate limiting keys on the request URI, not `getServletPath()`, which
  MockMvc leaves empty (BUG-SEC-003). `RateLimitIT` is the regression test
  and needs its own `@TestPropertySource` context.
- CAPTCHA gates registration and, after failures, sign-in
  (`TurnstileCaptchaService`); availability checks are rate-limited per IP.

## Uploads and outbound messages

- Every image upload goes through the one shared `ImageValidation`
  (`PHOTO_TYPES`, 2 MB) — avatar, logo, expense receipt, support screenshot
  — so a limit cannot drift between them (CR-073).
- A support screenshot is emailed and **never stored**; the log keeps name,
  type and size only.
- Secrets never appear in logs. Providers put API keys in headers and log
  the recipient and subject only. `.env*.example` files carry empty values.
- WhatsApp per-tenant tokens are encrypted at rest (CR-056); Twilio and
  SendGrid credentials are app-level configuration (CR-074).

## Developer diagnostics

Two independent server-side gates: the environment **and** the
`DEVELOPER_INSPECT` permission. Never weaken that to a frontend check, and
never add DevTools blocking — see the "Explicitly rejected" note in CR-045.

## Platform Admin Console

A separate identity and auth stack (CR-054), outside `AuthLayout` and outside
tenant scope, with mandatory MFA for platform staff. It does not get the shop
tour, the shop dashboard or the shop theme.

## When in doubt

If a change could let one tenant see another's data, weaken a gate, or move
a secret, stop and write the CR first. `SECURITY_REGISTRY.md` holds the
auth design and token rules; update it with the change.
