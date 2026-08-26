# Free hosting guide

How to put this app on the internet for ₹0/month, using three separate free
tiers: a database host, a backend host, and a static-site host for the
frontend.

---

## 1. The architecture

```
Browser
  │
  ▼
Cloudflare Pages  (frontend - React static build)
  │  same-origin request to /api/*
  │  (Pages proxies it server-side to Render - the browser never
  │   sees a different domain)
  ▼
Render.com  (backend - Spring Boot, Docker)
  │
  ▼
Neon.tech  (PostgreSQL 16)
```

| Layer | Service | Free tier gives you |
|---|---|---|
| Database | [Neon](https://neon.tech) | 1 project, 0.5 GB storage, autosuspends when idle (wakes on the next query) |
| Backend | [Render](https://render.com) | 1 web service, 512 MB RAM, sleeps after 15 min idle (~30-60s to wake) |
| Frontend | [Cloudflare Pages](https://pages.cloudflare.com) | Unlimited static requests/bandwidth, no sleep |

No credit card is required for any of the three at this scale.

### Why the frontend proxies to the backend instead of calling it directly

This matters and is easy to get wrong, so read this before deploying.

The login flow sets an `HttpOnly`, `Secure`, **`SameSite=Strict`** cookie
carrying the refresh token (`RefreshTokenCookieService.java`, CR-hardened
deliberately - JavaScript can never read a long-lived credential, so an XSS
bug can't exfiltrate it). A `SameSite=Strict` cookie is **never** sent on a
cross-site request, no matter what CORS headers say. If the frontend lived
on `myapp.pages.dev` and called the backend directly at
`myapp-api.onrender.com`, those are two different sites as far as the
browser is concerned - the refresh cookie would never be sent back, and
every user would be logged out the moment their 15-minute access token
expired.

The app does have a `JSON` refresh-token transport mode
(`app.security.refresh-token-transport`), but it's explicitly documented in
`SecurityProperties.java` as "only for non-browser clients (a future mobile
app)" - the React frontend never reads a refresh token from a JSON body, so
switching that env var would break login, not fix it.

The fix that needs **no backend code change**: make the browser think it's
only ever talking to one origin. Cloudflare Pages can proxy `/api/*`
requests to the Render backend at the edge, server-side - the browser
issues a same-origin request to its own domain, Cloudflare forwards it to
Render behind the scenes, and the cookie behaves exactly as designed. This
is step 4 below (`_redirects` file, one line).

---

## 2. Database - Neon (PostgreSQL 16)

1. Sign up at [neon.tech](https://neon.tech) (GitHub login works).
2. **Create a project.** Pick a region close to your users. Note the
   Postgres version offered - Neon defaults to the latest, which is fine;
   this app only needs 14+ features.
3. Neon gives you one database and role by default. From the project's
   **Connection Details** panel, copy:
   - Host (e.g. `ep-cool-name-123456.ap-southeast-1.aws.neon.tech`)
   - Database name
   - Username
   - Password
4. **Important - `sslmode=require`**: Neon requires SSL. The datasource URL
   in `application.yml` is
   `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?reWriteBatchedInserts=true` -
   it does not currently append `sslmode=require`. Add it via the `DB_NAME`
   env var itself (Spring just interpolates the string), e.g. set
   `DB_NAME=neondb?sslmode=require`, or add `&sslmode=require` if you also
   set other query params. Confirm this connects before moving on - a
   missing `sslmode` is the most common first-deploy failure with Neon.
5. Leave Flyway to create the schema - do not run any SQL by hand. The
   backend runs every `db/migration/V*.sql` file on its first boot (see
   §5).

**Free tier limits to know about**: 0.5 GB storage, and the compute
autosuspends after 5 minutes of inactivity (wakes automatically on the next
query, adds a few hundred ms to that one request - not the same as
Render's cold start, much shorter). Product/expense/logo images are stored
as `bytea` directly in Postgres in this schema (no separate object
storage) - if you upload a lot of product photos you will hit the 0.5 GB
ceiling faster than a typical hobby project. Watch usage in Neon's
dashboard.

---

## 3. Backend - Render (Docker Web Service)

A `Dockerfile` already exists at `backend/Dockerfile` (multi-stage: builds
with Maven, ships only the jar and a JRE). Render builds it directly - no
extra config needed beyond pointing Render at the repo.

1. Push this repo to GitHub (Render deploys from a Git remote, not a local
   folder).
2. On [render.com](https://render.com), **New → Web Service**, connect the
   repo.
3. Settings:
   - **Root directory**: `backend`
   - **Runtime**: Docker (Render detects the `Dockerfile` automatically)
   - **Instance type**: Free
   - **Health check path**: `/api/actuator/health`
4. **Environment variables** (Render → your service → Environment):

   | Variable | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DB_HOST` | your Neon host |
   | `DB_PORT` | `5432` |
   | `DB_NAME` | `neondb?sslmode=require` (see §2 step 4) |
   | `DB_USER` | your Neon role |
   | `DB_PASSWORD` | your Neon password |
   | `JWT_SECRET` | a real random 32+ byte value, base64-encoded (**never** the placeholder in `application.yml`) - generate one with `openssl rand -base64 32` |
   | `CORS_ORIGINS` | your Cloudflare Pages URL, e.g. `https://myapp.pages.dev` (kept as a safety net even though the proxy in §4 makes browser calls same-origin - Swagger/direct API testing still hits the backend's own origin) |
   | `COOKIE_SECURE` | `true` |
   | `APP_BOOTSTRAP_ENABLED` | `true` (**only for the first deploy** - see §6, then set back to `false` and redeploy) |
   | `APP_BOOTSTRAP_MOBILE` | the owner's mobile number |
   | `APP_BOOTSTRAP_EMAIL` | the owner's email |
   | `APP_BOOTSTRAP_PASSWORD` | a real password, meets the same validation as any other account |
   | `APP_BOOTSTRAP_NAME` | owner's full name |
   | `CAPTCHA_ENABLED` | `true` to require a sign-in security check (optional, see §8) |
   | `CAPTCHA_SITE_KEY` | Turnstile site key (only if enabled) |
   | `CAPTCHA_SECRET_KEY` | Turnstile secret key (only if enabled) |

   Everything else in `application.yml` has a working default (mail,
   AI assistant, rate limits) - leave them unset unless you specifically
   need SMTP email or the AI assistant working, which need their own real
   credentials (`MAIL_USER`/`MAIL_PASSWORD`, `GEMINI_API_KEY`, etc).

5. Deploy. Watch the build logs - Flyway logs every migration it applies
   on first boot (`V1` through the latest). If it fails on `sslmode`, go
   back to §2 step 4.
6. Once live, `https://<your-service>.onrender.com/api/actuator/health`
   should return `{"status":"UP"}`.

**Free tier limits to know about**: the service **sleeps after 15 minutes
with no traffic** and takes roughly 30-60 seconds to wake on the next
request - the first user of the day will see a slow load, not an error.
512 MB RAM is enough for this app at hobby-shop scale but leaves little
headroom; if the app OOMs, that's the first thing to check.

---

## 4. Frontend - Cloudflare Pages

1. On [pages.cloudflare.com](https://pages.cloudflare.com), **Create a
   project → Connect to Git**, pick this repo.
2. Build settings:
   - **Root directory**: `frontend`
   - **Build command**: `npm run build`
   - **Build output directory**: `dist`
3. **Do not set `VITE_API_BASE_URL`.** Leave it unset so the frontend
   defaults to relative `/api/...` calls (`apiClient.ts`) - that default is
   exactly what makes the proxy trick below work.
4. Add a `_redirects` file so Cloudflare Pages proxies API calls to Render
   at the edge, keeping the browser on one origin. Create
   `frontend/public/_redirects` (Vite copies anything in `public/`
   straight into `dist/`) with:

   ```
   /api/*  https://<your-service>.onrender.com/api/:splat  200
   ```

   The `200` status code is what tells Cloudflare Pages to **proxy** the
   request rather than issue a redirect the browser would follow (which
   would put the browser back on Render's origin and reintroduce the
   cookie problem this whole section exists to avoid).
5. Deploy. Cloudflare gives you a `https://<project>.pages.dev` URL
   immediately.
6. Go back to Render and set `CORS_ORIGINS` to this exact URL if you
   haven't already (§3).

**Free tier limits to know about**: none that matter at this scale -
Cloudflare Pages' free tier has no request or bandwidth cap for static
assets and the proxy feature used here.

---

## 5. First boot checklist

1. Confirm the backend health check is green (§3 step 6).
2. Visit the Cloudflare Pages URL. The login page should load.
3. Log in with the bootstrap owner credentials from §3 step 4.
4. **Immediately after confirming login works**: go back to Render, set
   `APP_BOOTSTRAP_ENABLED=false`, and redeploy. `BootstrapOwnerInitializer`
   only runs when this flag is true - leaving it on means every restart
   would try to recreate the same account. Production never creates an
   account silently otherwise (CR-008 - there is no self-registration
   endpoint for joining an existing shop, only `/v1/tenants/register` for
   a brand-new shop signing up, which stays available if you want it).
5. From the Owner account, create real staff/manager/accountant users
   through the app itself (Settings → Users) rather than reusing the
   bootstrap account for daily work.

---

## 6. Custom domain (optional, still free)

Both Render and Cloudflare Pages accept a custom domain for free (you only
pay for the domain name itself, typically ~₹700-1000/year from any
registrar). If you do this:
- Point your domain (or a subdomain) at Cloudflare Pages per their custom
  domain instructions.
- The `_redirects` proxy still works the same way - the backend can stay
  on its `onrender.com` URL, since the browser only ever talks to your
  custom domain.
- Update `RESET_URL` (password-reset email links) and `CORS_ORIGINS` on
  Render to the new domain.

---

## 7. Known limits of this setup, stated plainly

- **Cold starts**: the first request after 15 idle minutes on Render takes
  30-60s. Acceptable for a single small shop's usage pattern (bursty
  during business hours), not for anything latency-sensitive.
- **Storage**: Neon's 0.5 GB free tier includes every uploaded image
  (product photos, logos, signatures, QR codes, receipts) since they're
  stored as `bytea` columns, not a separate object store. A shop that
  uploads photos for hundreds of products will need to either upgrade
  Neon's plan or move image storage to something like Cloudflare R2's free
  tier (10 GB) - not implemented in this codebase today, a real follow-up
  if it becomes a problem.
- **Email/WhatsApp**: SMTP (`MAIL_USER`/`MAIL_PASSWORD`) and the AI
  assistant (`GEMINI_API_KEY`) still need their own real credentials -
  nothing about this hosting setup provides them. Left unconfigured, the
  app logs instead of sending/replying rather than crashing (an existing,
  deliberate convention - see `SmtpMailService`). Once deployed, prove
  email works with `POST /v1/settings/mail/test?toEmail=you@example.com`
  (needs `SETTINGS_MANAGE`) - it returns the mail server's own rejection
  text, which is the fastest way to diagnose a wrong app password.
- **No backups configured**: Neon's free tier does not include automated
  backups beyond a short point-in-time-recovery window. For anything
  beyond testing, export the database periodically (`pg_dump`) yourself.
- **Single instance**: no horizontal scaling, no staging environment - one
  free Render service is one process. Fine for one shop; not a path to
  multiple concurrent tenants at real scale without upgrading.

---

## 8. Sign-in security check (Cloudflare Turnstile)

Optional, free, and **off by default**. When off the app makes no request to
Cloudflare at all and the sign-in page is unchanged.

1. At [dash.cloudflare.com](https://dash.cloudflare.com) → **Turnstile** →
   *Add widget*. Add your Pages hostname (and `localhost` for development).
2. Copy the **site key** and **secret key**.
3. On Render, set `CAPTCHA_ENABLED=true`, `CAPTCHA_SITE_KEY=...`,
   `CAPTCHA_SECRET_KEY=...` and redeploy.

The login page asks `GET /v1/auth/captcha-config` on load, so it picks the
change up with no frontend rebuild.

**It fails safe in both directions**, which is the part worth trusting:

| Situation | What happens |
|---|---|
| Disabled, or either key blank | No challenge, sign-in works normally — a missing key can never lock you out |
| Enabled, no token sent | 400, sign-in refused |
| Enabled, token rejected | 400 — **correct credentials still do not sign in** |
| Enabled, Cloudflare unreachable | 503, sign-in refused rather than waved through |

To try it before you have real keys, Cloudflare publishes test keys —
always-passes is site `1x00000000000000000000AA` / secret
`1x0000000000000000000000000000000AA`, always-fails is site
`2x00000000000000000000AB` / secret `2x0000000000000000000000000000000AA`.

### A note on the health check

`management.health.mail.enabled` is set to `false`. Spring Boot otherwise
folds the SMTP indicator into the aggregate status, so a wrong mail password
made `/api/actuator/health` return **503** while the application was
perfectly healthy — and Render restarts any container that answers non-2xx
on its health check path. That would have restart-looped the deploy with
nothing in the logs pointing at mail. Mail problems surface through the
test-email endpoint instead, where they are actionable.
