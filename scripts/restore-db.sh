#!/usr/bin/env bash
# Restores a Hardware ERP database backup made by backup-db.sh.
#
# DESTRUCTIVE: this drops and recreates every table in the target
# database before loading the backup. Never run this against a database
# holding data you have not already backed up separately - if in doubt,
# run backup-db.sh first, even to overwrite something you think is
# disposable.
#
# Usage:
#   ./scripts/restore-db.sh backups/hardware_erp_20260823_190000.sql

set -euo pipefail

DB_CONTAINER="${DB_CONTAINER:-hardware-erp-postgres}"
DB_NAME="${DB_NAME:-hardware_erp}"
DB_USER="${DB_USER:-hardware_erp}"

BACKUP_FILE="${1:-}"
if [ -z "$BACKUP_FILE" ]; then
    echo "Usage: $0 <path-to-backup.sql>" >&2
    exit 1
fi
if [ ! -f "$BACKUP_FILE" ]; then
    echo "ERROR: backup file not found: $BACKUP_FILE" >&2
    exit 1
fi
if ! docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER"; then
    echo "ERROR: container '$DB_CONTAINER' is not running (docker ps shows no match)." >&2
    exit 1
fi

echo "This will DROP and recreate the schema in database '$DB_NAME'"
echo "(container '$DB_CONTAINER') and load: $BACKUP_FILE"
read -r -p "Type 'yes' to continue: " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
    echo "Aborted - nothing was changed."
    exit 1
fi

echo "Dropping and recreating schema 'public' ..."
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

echo "Loading $BACKUP_FILE ..."
docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" < "$BACKUP_FILE"

echo "Restore complete. Start the backend normally next - Flyway will see"
echo "the restored schema_version history and will not try to re-run"
echo "anything already applied in the backup."
