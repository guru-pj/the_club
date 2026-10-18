#!/usr/bin/env bash
# ============================================================
# The Club — database backups.
#
# On the host:   ./scripts/backup.sh            -> run a backup now (MySQL + PostgreSQL), via the backup containers
# In a container (db-backup-mysql / db-backup-postgres, BACKUP_ENGINE set):
#                backup.sh run                   -> dump this engine's databases into /backups
#                backup.sh schedule              -> run nightly at BACKUP_SCHEDULE (HH:MM, container TZ)
#
# Dumps: /backups/<engine>/<database>-<UTC timestamp>.sql.gz, kept for BACKUP_RETENTION_DAYS (default 14).
# Copy the backups volume off the host (S3/GCS/another server) for real disaster recovery — see docs/PRODUCTION.md.
# ============================================================
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
SCHEDULE="${BACKUP_SCHEDULE:-02:30}"
MYSQL_DATABASES="${MYSQL_DATABASES:-userdb authdb clubdb eventdb notificationdb}"
POSTGRES_DATABASES="${POSTGRES_DATABASES:-activitydb}"

log() { echo "[backup $(date -u +%Y-%m-%dT%H:%M:%SZ)] $*"; }

dump_mysql() {
    local dir="$BACKUP_DIR/mysql" defaults
    mkdir -p "$dir"
    defaults="$(mktemp)"
    chmod 600 "$defaults"
    printf '[client]\nuser=root\npassword=%s\nhost=%s\n' "$(cat /run/secrets/mysql-root-password)" "${MYSQL_HOST:-mysql}" > "$defaults"
    for db in $MYSQL_DATABASES; do
        local file="$dir/$db-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
        # --single-transaction: consistent InnoDB snapshot without locking writers
        if mysqldump --defaults-extra-file="$defaults" --single-transaction --routines --triggers --events \
                --set-gtid-purged=OFF --databases "$db" | gzip -9 > "$file.partial"; then
            mv "$file.partial" "$file"
            log "mysql $db -> $file ($(du -h "$file" | cut -f1))"
        else
            rm -f "$file.partial"
            log "ERROR: mysql dump of $db failed"
            failed=1
        fi
    done
    rm -f "$defaults"
}

dump_postgres() {
    local dir="$BACKUP_DIR/postgres"
    mkdir -p "$dir"
    export PGPASSWORD
    PGPASSWORD="$(cat /run/secrets/postgres-password)"
    for db in $POSTGRES_DATABASES; do
        local file="$dir/$db-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
        if pg_dump --host "${POSTGRES_HOST:-postgres}" --username "${POSTGRES_USER:-postgres}" --clean --if-exists \
                --create --no-password "$db" | gzip -9 > "$file.partial"; then
            mv "$file.partial" "$file"
            log "postgres $db -> $file ($(du -h "$file" | cut -f1))"
        else
            rm -f "$file.partial"
            log "ERROR: postgres dump of $db failed"
            failed=1
        fi
    done
}

# plain bash + coreutils: the slim mysql image has no findutils
prune() {
    local cutoff file
    cutoff=$(( $(date +%s) - RETENTION_DAYS * 86400 ))
    shopt -s nullglob
    for file in "$BACKUP_DIR"/*/*.sql.gz; do
        if [ "$(stat -c %Y "$file")" -lt "$cutoff" ]; then
            rm -f "$file"
            log "pruned $file"
        fi
    done
    shopt -u nullglob
}

run_once() {
    failed=0
    case "${BACKUP_ENGINE:-}" in
        mysql) dump_mysql ;;
        postgres) dump_postgres ;;
        *) echo "BACKUP_ENGINE must be mysql or postgres" >&2; exit 2 ;;
    esac
    prune
    return "$failed"
}

seconds_until() {
    local now target
    now=$(date +%s)
    target=$(date -d "today $1" +%s)
    [ "$target" -le "$now" ] && target=$(date -d "tomorrow $1" +%s)
    echo $(( target - now ))
}

schedule() {
    log "scheduler started (${BACKUP_ENGINE}); daily at $SCHEDULE, retention $RETENTION_DAYS days"
    while true; do
        sleep "$(seconds_until "$SCHEDULE")"
        run_once || log "backup finished with errors"
    done
}

case "${1:-host}" in
    run) run_once ;;
    schedule) schedule ;;
    host)
        cd "$(dirname "${BASH_SOURCE[0]}")/.."
        docker compose exec -T db-backup-mysql /opt/theclub/scripts/backup.sh run
        docker compose exec -T db-backup-postgres /opt/theclub/scripts/backup.sh run
        echo "✅ Backups written to the 'backups' volume. List them with:"
        echo "   docker compose exec db-backup-mysql ls -lh /backups/mysql /backups/postgres"
        ;;
    *) echo "usage: $0 [run|schedule]" >&2; exit 2 ;;
esac
