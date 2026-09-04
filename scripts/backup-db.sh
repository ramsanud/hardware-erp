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
# Works against either installation (CR-059) - db-target.sh works out which:
#
#   self-hosted   ./scripts/backup-db.sh
#                 (finds the running container; nothing to configure)
#
#   hosted        DB_HOST=xxx.pooler.supabase.com DB_USER=postgres.xxx \
#                 DB_NAME=postgres DB_PASSWORD='...' ./scripts/backup-db.sh
#                 (needs pg_dump installed locally)
#
# Output: backups/hardware_erp_YYYYMMDD_HHMMSS.sql (plain SQL, pg_dump -F p)
# - human-readable, diffable, restorable with plain `psql`, no pg_restore
#   version-matching to worry about.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/db-target.sh
source "$SCRIPT_DIR/db-target.sh"

resolve_db_target

BACKUP_DIR="$SCRIPT_DIR/../backups"
mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_FILE="$BACKUP_DIR/hardware_erp_${TIMESTAMP}.sql"

echo "Backing up $(db_target_description) ..."
db_dump --format=plain --no-owner --no-privileges > "$OUT_FILE"

SIZE="$(du -h "$OUT_FILE" | cut -f1)"
echo "Done: $OUT_FILE ($SIZE)"
echo ""
echo "This file contains real customer/supplier data (names, mobile numbers,"
echo "GSTINs, addresses). Supplier bank account numbers are stored encrypted"
echo "at rest (CR-018) so the dump holds ciphertext, not plaintext, for that"
echo "one field - everything else here is plain text. Keep this file out of"
echo "version control and delete old backups you no longer need."
echo ""
echo "A backup that has never been restored is a hope, not a backup. Restore"
echo "one into a scratch database at least once: ./scripts/restore-db.sh"
