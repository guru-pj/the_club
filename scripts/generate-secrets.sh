#!/usr/bin/env bash
# ============================================================
# The Club — generate deployment secrets as files.
#
# Creates ./secrets/ (or $SECRETS_DIR) with one file per secret. docker-compose.yml mounts each file into only
# the containers that need it (/run/secrets/...), where Spring reads it through
# SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/.
#
# Idempotent: existing files are kept. Use --force to regenerate everything (this invalidates all sessions and
# requires resetting database passwords — see docs/PRODUCTION.md).
#
# Usage: ./scripts/generate-secrets.sh [--force]
# Requires: openssl
# ============================================================
set -euo pipefail

FORCE=false
[ "${1:-}" = "--force" ] && FORCE=true

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS_DIR="${SECRETS_DIR:-$ROOT_DIR/secrets}"

if ! command -v openssl >/dev/null 2>&1; then
    echo "❌ openssl is required" >&2
    exit 1
fi

# The directory is private to the deploying user (700). Files are 644 because Compose mounts them read-only into
# containers that run as non-root users with other UIDs, which must be able to read them; host users other than the
# owner still cannot reach them through the 700 directory.
umask 022
mkdir -p "$SECRETS_DIR"
chmod 700 "$SECRETS_DIR"

created=()

# random URL-safe string of $1 bytes of entropy (no newline)
random_value() {
    openssl rand -base64 "$1" | tr -d '\n=+/' | cut -c1-"$(( $1 * 4 / 3 ))"
}

# write_secret <name> <value> — only if missing (or --force)
write_secret() {
    local file="$SECRETS_DIR/$1"
    if [ -f "$file" ] && [ "$FORCE" = false ]; then
        return
    fi
    printf '%s' "$2" > "$file"
    chmod 644 "$file"
    created+=("$1")
}

# --- JWT signing key pair (RS256). Private key: auth-service only. Public key: every service.
if [ ! -f "$SECRETS_DIR/theclub.jwt.private-key" ] || [ "$FORCE" = true ]; then
    tmp="$(mktemp "$SECRETS_DIR/.keygen.XXXXXX")"
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp" 2>/dev/null
    # PKCS#8 PEM ("BEGIN PRIVATE KEY")
    openssl pkcs8 -topk8 -nocrypt -in "$tmp" -out "$SECRETS_DIR/theclub.jwt.private-key"
    openssl pkey -in "$tmp" -pubout -out "$SECRETS_DIR/theclub.jwt.public-key"
    rm -f "$tmp"
    chmod 644 "$SECRETS_DIR/theclub.jwt.private-key" "$SECRETS_DIR/theclub.jwt.public-key"
    created+=("theclub.jwt.private-key" "theclub.jwt.public-key")
fi

# --- service-to-service key
write_secret "theclub.security.internal-api-key" "$(random_value 48)"

# --- database administrator passwords (used by the DB containers and the backup job)
write_secret "mysql-root-password" "$(random_value 24)"
write_secret "postgres-password" "$(random_value 24)"

# --- one least-privilege database user per service
for service in user auth club event notification activity; do
    write_secret "db-${service}-password" "$(random_value 24)"
done

# --- shared secret the ZeptoMail bounce webhook presents (?token=...); see docs/EMAIL_SETUP.md
write_secret "zeptomail-webhook-token" "$(random_value 32)"

# --- first platform admin
write_secret "admin-password" "$(random_value 12)"

# --- Grafana admin (observability stack, docker-compose.observability.yml); user name: admin
write_secret "grafana-admin-password" "$(random_value 20)"

# --- AES-256 key for Strava OAuth tokens at rest (base64 of 32 random bytes; keep it: losing it means members
#     must reconnect Strava)
write_secret "strava-token-encryption-key" "$(openssl rand -base64 32 | tr -d '\n')"

# --- Password of the Redis that shares rate limits and chat between replicas (Kubernetes: redis.enabled). Unused by
#     Docker Compose, which runs one replica of each service.
write_secret "redis-password" "$(random_value 32)"

# --- S3 secret key for uploaded images (profile pictures, club photos, event images). The bundled SeaweedFS reads
#     its access rules from seaweedfs-s3.json, rebuilt from this key on every run; with an external S3 store
#     (AWS, R2, ...) put that provider's secret key here instead.
write_secret "media-s3-secret-key" "$(random_value 40)"
media_key="$(cat "$SECRETS_DIR/media-s3-secret-key")"
s3_config="$SECRETS_DIR/seaweedfs-s3.json"
new_config="$(printf '{"identities":[{"name":"theclub","credentials":[{"accessKey":"theclub-media","secretKey":"%s"}],"actions":["Admin","Read","List","Write"]},{"name":"anonymous","actions":["Read:media"]}]}' "$media_key")"
if [ ! -f "$s3_config" ] || [ "$(cat "$s3_config")" != "$new_config" ]; then
    printf '%s' "$new_config" > "$s3_config"
    chmod 644 "$s3_config"
    created+=("seaweedfs-s3.json")
fi

# --- third-party credentials: created empty, fill in for production
for optional in smtp-password razorpay-key-secret razorpay-webhook-secret strava-client-secret zeptomail-token alertmanager-slack-webhook; do
    if [ ! -f "$SECRETS_DIR/$optional" ]; then
        : > "$SECRETS_DIR/$optional"
        chmod 644 "$SECRETS_DIR/$optional"
        created+=("$optional (empty — fill in for production)")
    fi
done

if [ ${#created[@]} -eq 0 ]; then
    echo "🔐 Secrets already present in $SECRETS_DIR (nothing changed)."
    exit 0
fi

echo "🔐 Wrote secrets to $SECRETS_DIR:"
for name in "${created[@]}"; do
    echo "   - $name"
done

if printf '%s\n' "${created[@]}" | grep -qx "admin-password"; then
    admin_email="admin@theclub.local"
    if [ -f "$ROOT_DIR/.env" ]; then
        admin_email="$(grep -E '^ADMIN_EMAIL=' "$ROOT_DIR/.env" | tail -1 | cut -d= -f2- || true)"
        admin_email="${admin_email:-admin@theclub.local}"
    fi
    echo ""
    echo "👤 Platform admin login (shown once — store it in your password manager):"
    echo "   email:    $admin_email"
    echo "   password: $(cat "$SECRETS_DIR/admin-password")"
fi
