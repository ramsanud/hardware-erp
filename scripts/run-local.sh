#!/usr/bin/env bash
# Runs the Hardware ERP backend on one developer's machine (the `local`
# profile) with the variables from .env exported first.
#
#   docker compose up -d
#   ./scripts/run-local.sh
#
# Why this exists: Spring Boot does NOT read .env files (see run-cloud.sh),
# and the documented local recipe - `mvn spring-boot:run` on its own - starts
# the backend with none of them. That is how a machine with working Gmail
# credentials in .env spent weeks "sending" every password-reset link and
# sign-in code to the log: EmailTransport.isConfigured() was false because
# MAIL_USER was never in the process environment, and the warning it logs
# ("Mail not configured") is easy to miss under the local profile's SQL
# output. Found 2026-09-13 while chasing "OTP email never arrives".
#
# The parser is run-cloud.sh's, line for line, and for the same reasons it
# is not `source`: a value with a space must not be executed, and a value
# with $(...) must not be run. A variable already set in the caller's
# environment wins over the file, so a one-off override on the command
# line - `MFA_REQUIRED=false ./scripts/run-local.sh` - works.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="${ENV_FILE:-$REPO_ROOT/.env}"

if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: $ENV_FILE not found." >&2
    echo "Create it from the template:  cp .env.example .env" >&2
    exit 1
fi

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
            value='' ;;
        *)
            value="$(printf '%s' "$value" | sed 's/[[:space:]]\{1,\}#.*$//')" ;;
    esac

    if [ -n "$value" ] && [ -z "${!key:-}" ]; then
        export "$key=$value"
    fi
done < "$ENV_FILE"

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"

# Say what mail will do, up front, because this is the thing the log buries.
# Keyed on EMAIL_PROVIDER first: the application constructs exactly one
# transport for that value, so a blank Resend key with provider=resend means
# "logged", not "falls back to SMTP" - and the banner must not imply otherwise.
case "${EMAIL_PROVIDER:-smtp}" in
    resend)
        if [ -n "${RESEND_API_KEY:-}" ] && [ -n "${RESEND_FROM_EMAIL:-}" ]; then
            MAIL_MODE="Resend as ${RESEND_FROM_EMAIL}"
        else
            MAIL_MODE="Resend selected but RESEND_API_KEY / RESEND_FROM_EMAIL blank - codes and links will be LOGGED, not sent"
        fi ;;
    sendgrid)
        if [ -n "${SENDGRID_API_KEY:-}" ] && [ -n "${SENDGRID_FROM_EMAIL:-}" ]; then
            MAIL_MODE="SendGrid as ${SENDGRID_FROM_EMAIL}"
        else
            MAIL_MODE="SendGrid selected but SENDGRID_API_KEY / SENDGRID_FROM_EMAIL blank - codes and links will be LOGGED, not sent"
        fi ;;
    *)
        if [ -n "${MAIL_USER:-}" ] && [ -n "${MAIL_PASSWORD:-}" ]; then
            MAIL_MODE="SMTP ${MAIL_HOST:-smtp.gmail.com} as ${MAIL_USER}"
        else
            MAIL_MODE="NOT CONFIGURED - codes and links will be LOGGED, not sent"
        fi ;;
esac

echo ""
echo "Starting Hardware ERP (local)"
echo "  profile  : $SPRING_PROFILES_ACTIVE"
echo "  env file : $ENV_FILE"
echo "  mail     : $MAIL_MODE"
echo "  frontend : run the Vite dev server in frontend/ in a second terminal"
echo ""

cd "$REPO_ROOT/backend"
exec mvn -o spring-boot:run
