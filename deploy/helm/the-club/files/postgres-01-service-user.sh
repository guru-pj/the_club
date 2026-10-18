#!/bin/bash
# Creates the activity-service role and makes it the owner of activitydb (and of any tables that already exist,
# e.g. created by an older release while connected as the superuser).
#
# Runs automatically on first initialisation (/docker-entrypoint-initdb.d). It is idempotent, so on an existing
# installation run it by hand:
#   docker compose exec postgres bash /docker-entrypoint-initdb.d/01-service-user.sh
set -euo pipefail

SECRETS=/run/secrets
export PGPASSWORD="$(cat "$SECRETS/postgres-password")"
superuser="${POSTGRES_USER:-postgres}"
database="${POSTGRES_DB:-activitydb}"
role=theclub_activity
password="$(cat "$SECRETS/db-activity-password")"

psql -v ON_ERROR_STOP=1 --username "$superuser" --dbname postgres <<SQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$role') THEN
        CREATE ROLE $role LOGIN PASSWORD '$password';
    ELSE
        ALTER ROLE $role WITH LOGIN PASSWORD '$password';
    END IF;
END
\$\$;
ALTER DATABASE $database OWNER TO $role;
REVOKE ALL ON DATABASE $database FROM PUBLIC;
GRANT CONNECT ON DATABASE $database TO $role;
SQL

psql -v ON_ERROR_STOP=1 --username "$superuser" --dbname "$database" <<SQL
ALTER SCHEMA public OWNER TO $role;
DO \$\$
DECLARE obj record;
BEGIN
    FOR obj IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tableowner <> '$role' LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO $role', obj.tablename);
    END LOOP;
    FOR obj IN SELECT sequencename FROM pg_sequences WHERE schemaname = 'public' AND sequenceowner <> '$role' LOOP
        EXECUTE format('ALTER SEQUENCE public.%I OWNER TO $role', obj.sequencename);
    END LOOP;
END
\$\$;
SQL
echo "postgres: $role owns $database"
