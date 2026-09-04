#!/usr/bin/env bash
# Restores a Hardware ERP database backup made by backup-db.sh.
#
# DESTRUCTIVE: this drops and recreates every table in the target
# database before loading the backup. Never run this against a database
# holding data you have not already backed up separately - if in doubt,
# run backup-db.sh first, even to overwrite something you think is
# disposable.
#
# Works against either installation (CR-059) - db-target.sh works out which:
#
#   self-hosted   ./scripts/restore-db.sh backups/hardware_erp_20260904_190000.sql
#   hosted        DB_HOST=... DB_USER=... DB_NAME=postgres DB_PASSWORD='...' \
#                 ./scripts/restore-db.sh backups/hardware_erp_20260904_190000.sql

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/db-target.sh
source "$SCRIPT_DIR/db-target.sh"

BACKUP_FILE="${1:-}"
if [ -z "$BACKUP_FILE" ]; then
    echo "Usage: $0 <path-to-backup.sql>" >&2
    exit 1
fi
if [ ! -f "$BACKUP_FILE" ]; then
    echo "ERROR: backup file not found: $BACKUP_FILE" >&2
    exit 1
fi

resolve_db_target

echo "This will DROP and recreate schema 'public' in"
echo "  $(db_target_description)"
echo "and load: $BACKUP_FILE"
# Restoring over a managed database is the one case where a typo destroys
# data that is not on this machine and may be shared by every tenant, so it
# asks for something harder to type by reflex than 'yes'.
if [ "$DB_TARGET_KIND" = "remote" ]; then
    echo ""
    echo "WARNING: this target is a MANAGED database, not a local container."
    echo "         On the hosted deployment that database holds EVERY tenant."
    read -r -p "Type the database host ('$DB_HOST') to continue: " CONFIRM
    EXPECTED="$DB_HOST"
else
    read -r -p "Type 'yes' to continue: " CONFIRM
    EXPECTED="yes"
fi
if [ "$CONFIRM" != "$EXPECTED" ]; then
    echo "Aborted - nothing was changed."
    exit 1
fi

echo "Dropping and recreating schema 'public' ..."
db_psql -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

echo "Loading $BACKUP_FILE ..."
db_psql < "$BACKUP_FILE"

echo "Restore complete. Start the backend normally next - Flyway will see"
echo "the restored schema_version history and will not try to re-run"
echo "anything already applied in the backup."
