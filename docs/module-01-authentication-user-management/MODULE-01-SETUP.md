# MODULE-01-SETUP

Everything needed to build and run Module 1 locally.

## Prerequisites

| Tool | Required | Verify with | Source of the requirement |
|---|---|---|---|
| JDK | **21** | `java -version` | `pom.xml` -> `java.version` |
| Maven | 3.9+ or bundled wrapper | `./mvnw -version` | Spring Boot 3.4.2 |
| Node.js | 20.19+ or 22.12+ | `node --version` | Vite 6 |
| npm | 10+ | `npm --version` | ships with Node |
| Docker | current | `docker --version` | PostgreSQL + Testcontainers |
| PostgreSQL | **16** | via Docker | `docker-compose.yml` |
| Git | current | `git --version` | - |

## Java setup

Install Eclipse Temurin 21 (adoptium.net).

```bash
# Windows
winget install EclipseAdoptium.Temurin.21.JDK
# macOS
brew install --cask temurin@21
# Ubuntu
sudo apt install openjdk-21-jdk
```

Verify: `java -version` must print `21.x.x`. If it prints 17, fix `JAVA_HOME`.

## Maven setup

You do not need to install Maven. The repository ships the wrapper:

```bash
cd backend
./mvnw -version        # macOS / Linux
mvnw.cmd -version      # Windows
```

The wrapper downloads the correct Maven version, so every developer uses the
same one.

## PostgreSQL setup

```bash
cp .env.example .env        # then set DB_PASSWORD
docker compose up -d
docker compose ps           # wait for STATUS = healthy
```

`docker-compose.yml` provisions PostgreSQL 16-alpine, database `hardware_erp`,
user `hardware_erp`, encoding UTF8, timezone Asia/Kolkata, with a named volume
so data survives `docker compose down`.

Connection settings used by the backend:

| Setting | Value | Env var |
|---|---|---|
| URL | `jdbc:postgresql://localhost:5432/hardware_erp` | `DB_HOST`, `DB_PORT`, `DB_NAME` |
| User | `hardware_erp` | `DB_USER` |
| Driver | `org.postgresql.Driver` | fixed |

## IntelliJ IDEA setup

1. **File > Open**, select the `backend` folder (not the repository root) so
   IntelliJ detects `pom.xml`.
2. **File > Project Structure > Project SDK** -> Java 21.
3. Install the **Lombok** plugin if it is not bundled.
4. **Settings > Build > Compiler > Annotation Processors** -> tick
   *Enable annotation processing*. Without this, every Lombok-generated getter
   shows as a compile error.
5. Run configuration: main class `com.hardware.erp.HardwareErpApplication`,
   environment `SPRING_PROFILES_ACTIVE=dev`.

For the frontend, open the `frontend` folder in VS Code and add the
*Tailwind CSS IntelliSense* extension.

## Run instructions

Three terminals:

```bash
# 1 - database
docker compose up -d

# 2 - backend  (http://localhost:8080/api)
cd backend && ./mvnw spring-boot:run

# 3 - frontend (http://localhost:5173)
cd frontend && npm install && npm run dev
```

Sign in with `9876543210` / `Owner@2026` (dev seed data only).

## Build instructions

```bash
# Backend: compile, test, package
cd backend
./mvnw clean verify                 # requires Docker for Testcontainers
./mvnw clean package -DskipTests    # JAR only
java -jar target/hardware-erp-1.0.0.jar

# Frontend
cd frontend
npm run typecheck                   # tsc -b --force
npm run build                       # emits dist/
```

## Flyway instructions

Flyway owns the schema. Hibernate runs with `ddl-auto: validate` and never
alters anything.

| File | Location | Loaded in |
|---|---|---|
| `V1__auth_schema.sql` | `db/migration/` | all environments |
| `V900__seed_dev_data.sql` | `db/seed/` | dev and test only |

```yaml
# application-dev.yml
spring.flyway.locations: classpath:db/migration,classpath:db/seed
# application-prod.yml
spring.flyway.locations: classpath:db/migration
```

Production physically cannot load seed accounts.

Inspect what has run:

```bash
docker exec -it hardware-erp-postgres psql -U hardware_erp -d hardware_erp \
  -c "SELECT version, description, success FROM flyway_schema_history;"
```

Rules:
- Never edit a migration that has already run; add a new version instead.
- Never `ALTER TABLE` a production database by hand.
- Reset in development only: `docker compose down -v && docker compose up -d`.

## Profiles

| Profile | Purpose | Notable differences |
|---|---|---|
| `dev` | local development | seed data loaded, rate limits relaxed, reset links logged, `cookie-secure: false` |
| `test` | automated tests | Testcontainers PostgreSQL, rate limiting off by default |
| `prod` | deployment | schema only, `cookie-secure: true`, refuses to start with the placeholder JWT secret |

## Required environment variables

See `.env.example`. In production these must all be set explicitly:

```
DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD
JWT_SECRET                 # openssl rand -base64 32
CORS_ORIGINS
COOKIE_SECURE=true
APP_BOOTSTRAP_ENABLED      # true for the very first start only
APP_BOOTSTRAP_MOBILE APP_BOOTSTRAP_EMAIL APP_BOOTSTRAP_PASSWORD
MAIL_HOST MAIL_PORT MAIL_USER MAIL_PASSWORD RESET_URL
```
