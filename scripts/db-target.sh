#!/usr/bin/env bash
# CR-059 - works out HOW to reach this installation's database, so that
# backup-db.sh and restore-db.sh do not each carry two code paths.
#
# Sourced, never executed directly.
#
# There are two kinds of installation and they are reached differently:
#
#   SELF_HOSTED   PostgreSQL is a container on this machine and its port is
#                 deliberately NOT published (docker-compose.selfhosted.yml).
#                 The only way in is `docker exec`, which also means pg_dump
#                 always matches the server version exactly.
#
#   CLOUD         PostgreSQL is managed (Supabase and the like). There is no
#                 container to exec into, so this needs pg_dump/psql
#                 installed on whatever machine runs the script, and a
#                 password in the environment.
#
# Which one is chosen is inferred from DB_HOST rather than from
# APP_DEPLOYMENT_MODE, because these scripts are run by a person at a shell
# who has not necessarily exported the application's environment - and the
# host is the thing that actually decides whether a container is involved.

set -euo pipefail

DB_NAME="${DB_NAME:-hardware_erp}"
DB_USER="${DB_USER:-hardware_erp}"
DB_PORT="${DB_PORT:-5432}"

# Container names this project is known to use, newest first:
#   hardware-erp-db        docker-compose.selfhosted.yml (the client install)
#   hardware-erp-postgres  docker-compose.yml            (developer machine)
CANDIDATE_CONTAINERS=("hardware-erp-db" "hardware-erp-postgres")

# Host values that mean "the database is a container on this machine", not a
# remote server. Kept in step with DeploymentModeGuard.LOCAL_DB_FRAGMENTS.
_is_local_host() {
    case "${1:-}" in
        ""|localhost|127.0.0.1|postgres|db|host.docker.internal) return 0 ;;
        *) return 1 ;;
    esac
}

# Sets DB_TARGET_KIND (container|remote) and, for a container, DB_CONTAINER.
resolve_db_target() {
    if [ -n "${DB_HOST:-}" ] && ! _is_local_host "$DB_HOST"; then
        DB_TARGET_KIND="remote"

        if ! command -v pg_dump >/dev/null 2>&1; then
            echo "ERROR: DB_HOST is '$DB_HOST' (a managed database), but pg_dump is not installed here." >&2
            echo "       Install the PostgreSQL client tools, or run this on a machine that has them." >&2
            echo "       A hosted deployment usually also has provider-side backups - check those first." >&2
            exit 1
        fi
        if [ -z "${DB_PASSWORD:-}" ]; then
            echo "ERROR: DB_PASSWORD is not set, and a managed database will not accept a connection without it." >&2
            echo "       Export it for this command only, so it does not persist in shell history:" >&2
            echo "         DB_PASSWORD='...' $0 $*" >&2
            exit 1
        fi
        # Exported for pg_dump/psql. Passing it as a command-line argument
        # would put the password in `ps` output for every user on the box.
        export PGPASSWORD="$DB_PASSWORD"
        return 0
    fi

    DB_TARGET_KIND="container"

    # An explicit DB_CONTAINER always wins; otherwise take the first candidate
    # that is actually running, so the same script serves a client install and
    # a developer machine with no arguments.
    if [ -n "${DB_CONTAINER:-}" ]; then
        CANDIDATE_CONTAINERS=("$DB_CONTAINER")
    fi

    if ! command -v docker >/dev/null 2>&1; then
        echo "ERROR: no DB_HOST set and docker is not installed, so there is no database to reach." >&2
        exit 1
    fi

    local running
    running="$(docker ps --format '{{.Names}}')"
    for candidate in "${CANDIDATE_CONTAINERS[@]}"; do
        if echo "$running" | grep -qx "$candidate"; then
            DB_CONTAINER="$candidate"
            return 0
        fi
    done

    echo "ERROR: no running PostgreSQL container found (looked for: ${CANDIDATE_CONTAINERS[*]})." >&2
    echo "       Start it first:" >&2
    echo "         docker compose -f docker-compose.selfhosted.yml up -d   # client install" >&2
    echo "         docker compose up -d                                    # developer machine" >&2
    echo "       Or set DB_HOST/DB_USER/DB_PASSWORD to reach a managed database instead." >&2
    exit 1
}

# Human-readable description of what was resolved, for the script's own output.
db_target_description() {
    if [ "$DB_TARGET_KIND" = "remote" ]; then
        echo "managed database $DB_HOST:$DB_PORT/$DB_NAME"
    else
        echo "container '$DB_CONTAINER' ($DB_NAME)"
    fi
}

# pg_dump against whichever target was resolved. Arguments are passed through.
db_dump() {
    if [ "$DB_TARGET_KIND" = "remote" ]; then
        pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" "$@"
    else
        docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" "$@"
    fi
}

# psql against whichever target was resolved, reading stdin where given.
db_psql() {
    if [ "$DB_TARGET_KIND" = "remote" ]; then
        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 "$@"
    else
        docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 "$@"
    fi
}
