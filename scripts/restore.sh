#!/usr/bin/env bash
# ============================================================
# The Club — restore ONE database from a backup made by scripts/backup.sh.
#
# Usage: ./scripts/restore.sh <database> <dump>
#   <database>  userdb | authdb | clubdb | eventdb | notificationdb | activitydb
#   <dump>      a .sql.gz file on the host, or a path inside the backups volume
#               (e.g. mysql/userdb-20261018T023000Z.sql.gz); list them with:
#               docker compose exec db-backup-mysql ls -lh /backups/mysql /backups/postgres
#
# The current contents of the database are REPLACED. Stop the owning service first so nothing writes meanwhile:
#   docker compose stop user-service && ./scripts/restore.sh userdb ... && docker compose start user-service
# ============================================================
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [ $# -ne 2 ]; then
    sed -n '3,13p' "$0"
    exit 2
fi
database="$1"
dump="$2"

case "$database" in
    userdb|authdb|clubdb|eventdb|notificationdb) engine=mysql ;;
    activitydb) engine=postgres ;;
    *) echo "Unknown database: $database" >&2; exit 2 ;;
esac

if [ -f "$dump" ]; then
    read_dump() { cat "$dump"; }
    source_desc="host file $dump"
else
    container="db-backup-$engine"
    docker compose exec -T "$container" test -f "/backups/$dump" || { echo "Dump not found: $dump" >&2; exit 1; }
    read_dump() { docker compose exec -T "$container" cat "/backups/$dump"; }
    source_desc="backups volume /backups/$dump"
fi

echo "⚠️  This replaces ALL data in '$database' ($engine) with $source_desc."
read -r -p "Type the database name to confirm: " answer
if [ "$answer" != "$database" ]; then
    echo "Aborted."
    exit 1
fi

if [ "$engine" = mysql ]; then
    # dumps are made with --databases, so they recreate and select the database themselves
    read_dump | gunzip | docker compose exec -T mysql \
        sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql-root-password)" mysql -uroot'
else
    # dumps are made with --clean --create: connect to the maintenance database and let the dump recreate it
    read_dump | gunzip | docker compose exec -T postgres \
        sh -c 'PGPASSWORD="$(cat /run/secrets/postgres-password)" psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d postgres'
    # ownership of the recreated database goes back to the service role
    docker compose exec -T postgres bash /docker-entrypoint-initdb.d/01-service-user.sh
fi
echo "✅ Restored $database. Start the service again if you stopped it."
