# Deployment modes — hosted or self-hosted

CR-059. Hardware ERP ships as **one codebase in two shapes**:

| | **CLOUD** (hosted) | **SELF_HOSTED** (Docker) |
|---|---|---|
| Who runs it | You | The client, on their own machine |
| Database | Managed PostgreSQL (Supabase) | A PostgreSQL container on that machine |
| API | Render (or any container host) | A container on that machine |
| Frontend | Vercel | nginx container on that machine |
| Reached at | `https://shop.example.com` | `http://192.168.1.10` on the shop LAN |
| Internet needed | Yes | **No** (except for email / WhatsApp / AI, all optional) |
| Subscription billing | Live | **Off** — the client bought the software |
| Backups | Provider snapshots + `scripts/backup-db.sh` | `scripts/backup-db.sh` — **nobody else is doing this for them** |

**The application code is identical.** Supabase is used as a managed
PostgreSQL endpoint and nothing more — there is no Supabase SDK, no
Supabase Auth and no Supabase Storage anywhere in this project. Uploads
are `bytea` in PostgreSQL and authentication is this application's own JWT
+ MFA. Moving to Neon, RDS or a self-managed server is a change of
`DB_HOST`, not a change of mode.

---

## The switch

```bash
SPRING_PROFILES_ACTIVE=prod,cloud         # hosted SaaS
SPRING_PROFILES_ACTIVE=prod,selfhosted    # the client's own Docker box
```

Both layer **on top of** `prod`, never instead of it. `prod` owns the
security posture — no springdoc, no actuator beyond `/health`, no stack
traces, no developer inspection — and self-hosting relaxes none of it. A
shop's LAN is not a trusted network.

The mode is also readable as `app.deployment.mode` and overridable with
`APP_DEPLOYMENT_MODE`. It defaults to `CLOUD`, so every environment that
existed before CR-059 behaves exactly as it did.

### It refuses to start when the configuration cannot be right

`DeploymentModeGuard` fails the boot rather than letting a mistake surface
as wrong data hours later:

| Configuration | Refused because |
|---|---|
| `cloud` **and** `selfhosted` both active | opposite defaults; the winner would depend on profile ordering |
| `SELF_HOSTED` + a managed database host | the client's shop data would be written **into the multi-tenant SaaS database** |
| `CLOUD` + a container/localhost database | hosted instances have ephemeral storage — it would serve happily and lose every write on the next redeploy |
| `CLOUD` + `COOKIE_SECURE=false` | the 7-day refresh credential would cross plain HTTP |

All but the first only apply under `prod`, so local development is
untouched. On a successful start it prints a banner naming the mode, the
database host and kind, `cookie-secure`, and whether billing applies.

---

## Self-hosted: installing at a client site

### 1. Prerequisites

Docker Desktop (Windows/macOS) or Docker Engine + compose plugin (Linux).
Nothing else — no Java, no Node, no PostgreSQL on the host.

### 2. Configure

```bash
cp .env.selfhosted.example .env
```

Fill in the five required values. Generate the secrets separately:

```bash
# Git Bash. openssl ships with Git for Windows (/mingw64/bin/openssl).
openssl rand -base64 24    # DB_PASSWORD
openssl rand -base64 32    # JWT_SECRET
openssl rand -base64 32    # PLATFORM_ADMIN_JWT_SECRET  (a DIFFERENT value)
```

```powershell
# PowerShell. openssl is NOT on PATH here even though Git bundles it, so
# use the bundled generator instead - same OS cryptographic RNG.
.\scripts\new-secret.ps1 -Count 3
```

The two JWT secrets must differ: the Platform Admin Console is a separate
trust boundary from the shop app, and one shared value would let a token
from either side be replayed at the other. `JwtSecretGuard` refuses to start
in `prod` if either is a placeholder **or** if the two are equal
(BUG-SEC-004).

> **Write comments on their own line in a `.env` file, never after the `=`.**
> `APP_BOOTSTRAP_MOBILE=   # 10 digits` sets the owner's mobile number to the
> literal string `# 10 digits` in Docker Compose and in a naive parser. The
> shipped templates were corrected for exactly this.

Set `APP_BASE_URL` to the machine's **LAN IP**, not `localhost` —
`localhost` works only on that one machine, and the point of self-hosting
is that the counter PC, the billing desk and the owner's laptop all reach
it. Give the machine a static or DHCP-reserved address, or the URL changes
under everyone the next time the router reboots.

### 3. Start

```bash
docker compose -f docker-compose.selfhosted.yml up -d
```

First boot builds both images and runs every Flyway migration; allow a few
minutes. Then open `http://<that-ip>/` and sign in with the bootstrap
owner account.

### 4. Immediately after the first sign-in

1. Change the owner password.
2. Set `APP_BOOTSTRAP_ENABLED=false` in `.env` and run `up -d` again.
   There is no self-registration endpoint by design (CR-008); the
   bootstrap account is the only way in, and it should not stay armed.
3. Take a backup and **restore it once** into a scratch database. A backup
   that has never been restored is a hope, not a backup.

### Everyday operations

```bash
docker compose -f docker-compose.selfhosted.yml ps        # health
docker compose -f docker-compose.selfhosted.yml logs -f backend
docker compose -f docker-compose.selfhosted.yml restart backend
./scripts/backup-db.sh                                    # before ANY update
docker compose -f docker-compose.selfhosted.yml pull && \
  docker compose -f docker-compose.selfhosted.yml up -d --build   # update
```

Only the web container publishes a port. The database and the API are
reachable on the compose network and nowhere else, so a laptop on the
shop's wifi cannot connect to PostgreSQL directly even with the password.

---

## Two things about self-hosted that are easy to get wrong

### `COOKIE_SECURE` on a plain-HTTP LAN

Defaults to `false`, deliberately. A browser **never returns a Secure
cookie over http**, so with it on, sign-in appears to succeed and then
bounces straight back to the login page — with nothing wrong in the logs.
That is the single most likely support call.

The cost is real and worth stating to the client: the refresh token
crosses the shop's LAN unencrypted. That is a reasonable trade on a
private wired network and a bad one on shared or guest wifi. Once there is
real HTTPS in front of the install — a reverse proxy with a certificate,
or a VPN/Tailscale address — set `COOKIE_SECURE=true`.

### Backups are entirely the client's responsibility

Nobody is taking snapshots for them. The data lives in one Docker volume
(`hardware-erp-selfhosted-data`). Schedule `scripts/backup-db.sh` (Task
Scheduler or cron), and keep at least one copy **off that machine** — a
backup on the same disk does not survive the failure it exists for.

---

## Hosted: what differs

`render.yaml` is the blueprint; `docs/DEPLOYMENT_FREE_HOSTING.md` covers
the free-tier specifics. Two things that bite:

- Use the Supabase **session** pooler (port 5432), never the transaction
  pooler (6543). Transaction-mode pooling breaks server-side prepared
  statements and per-session state, and `DocumentSequenceService` holds
  `SELECT … FOR UPDATE` for the caller's transaction (CR-041).
- `baseline-version: 0` in `application-prod.yml` is load-bearing. A
  Supabase database is not a virgin schema, and Flyway's default baseline
  of 1 would skip V1 entirely — see BUG-DEPLOY-001.

---

## Backups against either target

`scripts/backup-db.sh` and `scripts/restore-db.sh` work in both modes.
They infer the target from `DB_HOST`:

```bash
# self-hosted — finds the running container, nothing to configure
./scripts/backup-db.sh

# hosted — needs pg_dump installed locally
DB_HOST=xxxxx.pooler.supabase.com DB_USER=postgres.xxxxx \
  DB_NAME=postgres DB_PASSWORD='...' ./scripts/backup-db.sh
```

`restore-db.sh` is destructive. Against a managed host it demands the host
name typed back rather than `yes`, because on the hosted deployment that
database holds **every** tenant.

---

## Billing in self-hosted mode

The client bought the software, so:

- `/v1/billing/checkout` and `/v1/billing/verify` return
  `503 BILLING_NOT_APPLICABLE`.
- The Razorpay webhook is rejected before its body is parsed.
- **Subscription tier caps are not enforced**, and `/v1/settings/usage`
  reports every limit as unlimited. Without this, an install shipping on
  the `FREE` tier would stop the client at their 101st customer and tell
  them to "upgrade the plan in Shop Settings" — a dead end, since there is
  no checkout to reach.
- The plan, upgrade and coupon cards are hidden in Shop Settings.

A reseller who runs self-hosted instances they *do* bill for can set
`APP_BILLING_ENABLED=true` and configure Razorpay normally.
