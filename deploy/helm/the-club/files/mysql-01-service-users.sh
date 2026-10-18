#!/bin/bash
# Creates one database and one least-privilege user per service. Each user can only touch its own database.
#
# Runs automatically when the MySQL volume is first initialised (/docker-entrypoint-initdb.d). It is idempotent,
# so on an existing installation run it by hand:
#   docker compose exec mysql bash /docker-entrypoint-initdb.d/01-service-users.sh
set -euo pipefail

SECRETS=/run/secrets
root_password="$(cat "$SECRETS/mysql-root-password")"

defaults="$(mktemp)"
trap 'rm -f "$defaults"' EXIT
chmod 600 "$defaults"
printf '[client]\nuser=root\npassword=%s\n' "$root_password" > "$defaults"

# database:user:password-secret
for entry in userdb:theclub_user:db-user-password \
             authdb:theclub_auth:db-auth-password \
             clubdb:theclub_club:db-club-password \
             eventdb:theclub_event:db-event-password \
             notificationdb:theclub_notification:db-notification-password; do
    IFS=: read -r database user secret <<< "$entry"
    password="$(cat "$SECRETS/$secret")"
    mysql --defaults-extra-file="$defaults" <<SQL
CREATE DATABASE IF NOT EXISTS \`$database\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$user'@'%' IDENTIFIED BY '$password';
ALTER USER '$user'@'%' IDENTIFIED BY '$password';
GRANT ALL PRIVILEGES ON \`$database\`.* TO '$user'@'%';
SQL
    echo "mysql: $user -> $database"
done
mysql --defaults-extra-file="$defaults" -e "FLUSH PRIVILEGES;"
