#!/usr/bin/env bash
# Runs the Hardware ERP backend against Supabase (CR-059, CLOUD mode).
#
#   cp .env.cloud.example .env.cloud     # then fill it in
#   ./scripts/run-cloud.sh
#
# This script exists because Spring Boot does NOT read .env files - there is
# no dotenv library in this project. Without it you would have to export a
# dozen variables by hand every time, and a single typo in DB_HOST produces a
# connection error that looks nothing like the actual mistake.
#
# scripts/run-cloud.ps1 is the PowerShell equivalent (the primary shell on the
# development machine). Neither is used when deploying to Render - there the
# same keys are set in the dashboard; see render.yaml.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$REPO_ROOT/.env.cloud"

if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: .env.cloud not found at $ENV_FILE" >&2
    echo "" >&2
    echo "Create it from the template and fill in your Supabase details:" >&2
    echo "    cp .env.cloud.example .env.cloud" >&2
    exit 1
fi

# Parsed line by line, deliberately NOT `source`d. Two reasons, both real:
#
#   1. `source` runs the file as shell. An unquoted value with a space -
#      APP_BOOTSTRAP_NAME=Shop Owner - assigns "Shop" and then tries to
#      execute "Owner", which is exactly what happened on the first run here
#      ("./.env.cloud: line 75: Owner: command not found"). The owner account
#      would have been created named "Shop".
#   2. Sourcing executes whatever is in the file. A value containing $(...)
#      or a backtick would run as a command. An env file should be data.
#
# Handles CRLF, surrounding quotes, comment-only values and trailing comments.
while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"

    case "$line" in
        ''|'#'*) continue ;;
    esac

    key="${line%%=*}"
    [ "$key" = "$line" ] && continue

    value="${line#*=}"
    key="$(printf '%s' "$key" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
    value="$(printf '%s' "$value" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"

    case "$key" in
        [A-Za-z_]*) ;;
        *) continue ;;
    esac

    case "$value" in
        '"'*'"')
            value="${value#\"}"; value="${value%\"}" ;;
        "'"*"'")
            value="${value#\'}"; value="${value%\'}" ;;
        '#'*)
            # "KEY=   # explanation" - blank key, the rest is documentation.
            value='' ;;
        *)
            value="$(printf '%s' "$value" | sed 's/[[:space:]]\{1,\}#.*$//')" ;;
    esac

    if [ -n "$value" ]; then
        export "$key=$value"
    fi
done < "$ENV_FILE"

# Fail here rather than letting Spring fail on an unresolvable placeholder -
# DB_HOST has no default in application-cloud.yml, and the resulting error
# names a property, not the thing you actually forgot to fill in.
MISSING=()
for var in DB_HOST DB_USER DB_PASSWORD JWT_SECRET PLATFORM_ADMIN_JWT_SECRET; do
    if [ -z "${!var:-}" ]; then
        MISSING+=("$var")
    fi
done

if [ ${#MISSING[@]} -gt 0 ]; then
    echo "ERROR: .env.cloud is missing required values:" >&2
    printf '    %s\n' "${MISSING[@]}" >&2
    echo "" >&2
    echo "Generate the two secrets with (run it twice, they must differ):" >&2
    echo "    openssl rand -base64 32" >&2
    exit 1
fi

if [ "$JWT_SECRET" = "$PLATFORM_ADMIN_JWT_SECRET" ]; then
    echo "ERROR: JWT_SECRET and PLATFORM_ADMIN_JWT_SECRET are the same value." >&2
    echo "The Platform Admin Console is a separate trust boundary from the shop app" >&2
    echo "(CR-054). One shared key lets a token from either side be replayed at the" >&2
    echo "other. Generate a second, different value." >&2
    exit 1
fi

# 6543 is Supabase's transaction pooler. It breaks server-side prepared
# statements and the SELECT ... FOR UPDATE that DocumentSequenceService holds
# for the caller's transaction (CR-041). The failure is intermittent and
# baffling, so refuse it outright rather than let it be debugged later.
if [ "${DB_PORT:-5432}" = "6543" ]; then
    echo "ERROR: DB_PORT is 6543 - that is Supabase's TRANSACTION pooler." >&2
    echo "Use the SESSION pooler on port 5432 instead (or just remove DB_PORT;" >&2
    echo "5432 is the default). See CR-041 for why this matters." >&2
    exit 1
fi

export SPRING_PROFILES_ACTIVE="prod,cloud"

echo ""
echo "Starting Hardware ERP against Supabase"
echo "  profiles : prod,cloud"
echo "  database : $DB_HOST"
echo "  frontend : run 'npm run dev' in frontend/ in a second terminal"
echo ""
echo "The startup banner will confirm the mode; DeploymentModeGuard refuses"
echo "to start on a combination that cannot be right."
echo ""

cd "$REPO_ROOT/backend"
mvn spring-boot:run
