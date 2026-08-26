#!/usr/bin/env bash
# Backs up the Hardware ERP PostgreSQL database before an app update.
#
# Run this BEFORE any deploy/update that touches the backend, and before
# any manual Flyway/database work. Restoring afterwards (restore-db.sh)
# is the actual guarantee against data loss - Flyway's "never edit an
# applied migration" rule and Hibernate's ddl-auto=validate protect
# against a *schema* mistake destroying data, but neither one protects
# against a bad deploy, a wrong command, or a disk failure. A real backup
# does.
#
# Usage:
#   ./scripts/backup-db.sh                # uses the same defaults the app itself uses
#   DB_CONTAINER=my-postgres ./scripts/backup-db.sh
#
# Output: backups/hardware_erp_YYYYMMDD_HHMMSS.sql (plain SQL, pg_dump -F p)
# - human-readable, diffable, restorable with plain `psql`, no pg_restore
#   version-matching to worry about.

set -euo pipefail

DB_CONTAINER="${DB_CONTAINER:-hardware-erp-postgres}"
DB_NAME="${DB_NAME:-hardware_erp}"
DB_USER="${DB_USER:-hardware_erp}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="$SCRIPT_DIR/../backups"
mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_FILE="$BACKUP_DIR/hardware_erp_${TIMESTAMP}.sql"

if ! docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER"; then
    echo "ERROR: container '$DB_CONTAINER' is not running (docker ps shows no match)." >&2
    echo "Start it first: docker compose up -d" >&2
    exit 1
fi

echo "Backing up database '$DB_NAME' from container '$DB_CONTAINER' ..."
docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" --format=plain --no-owner --no-privileges > "$OUT_FILE"

SIZE="$(du -h "$OUT_FILE" | cut -f1)"
echo "Done: $OUT_FILE ($SIZE)"
echo ""
echo "This file contains real customer/supplier data (names, mobile numbers,"
echo "GSTINs, addresses). Supplier bank account numbers are stored encrypted"
echo "at rest (CR-018) so the dump holds ciphertext, not plaintext, for that"
echo "one field - everything else here is plain text. Keep this file out of"
echo "version control and delete old backups you no longer need."
