#!/bin/sh
# Renders the Alertmanager config at container start: routing and inhibit rules come from alertmanager.yml, and
# the receiver is built from the same mail settings the application uses, so alerts are delivered without
# separate configuration:
#   dev profile            -> Mailpit (mailpit:1025), like every other email in development
#   MAIL_PROVIDER=zeptomail -> ZeptoMail SMTP with secrets/zeptomail-token
#   MAIL_PROVIDER=smtp      -> MAIL_HOST:MAIL_PORT with MAIL_USERNAME and secrets/smtp-password
# Email goes to ALERT_EMAIL_TO (default ADMIN_EMAIL) from ALERT_EMAIL_FROM (default MAIL_FROM). A Slack incoming
# webhook in secrets/alertmanager-slack-webhook adds a Slack receiver as well.
# Under the prod profile Alertmanager refuses to start when no receiver can deliver, unless
# ALERT_DELIVERY=none says so explicitly (see docs/OBSERVABILITY.md, "Alert delivery").
set -eu

BASE=/etc/alertmanager/alertmanager.yml
OUT=/alertmanager/alertmanager.rendered.yml
SECRETS=/run/secrets

fail() { echo "alertmanager: $*" >&2; exit 1; }
warn() { echo "alertmanager: WARNING: $*" >&2; }
# Values come from .env and are written as double-quoted YAML scalars: reject anything that could break out.
# Called in the main shell (not inside $(...)) so that fail really stops the container.
check() {
  case "$1" in *'"'* | *'\'* | *'
'*) fail "invalid character in $2" ;; esac
}
nonempty() { [ -s "$1" ] && [ -n "$(tr -d ' \r\n\t' < "$1")" ]; }

profile=${APP_PROFILE:-dev}
to=${ALERT_EMAIL_TO:-${ADMIN_EMAIL:-}}
from=${ALERT_EMAIL_FROM:-${MAIL_FROM:-}}
smarthost='' username='' password_file='' require_tls=true

if [ "${ALERT_DELIVERY:-}" = none ]; then
  :
elif [ "$profile" != prod ]; then
  smarthost=mailpit:1025
  require_tls=false
else
  case "${MAIL_PROVIDER:-}" in
    zeptomail)
      # ZeptoMail SMTP is in the same region as the API (api.zeptomail.in -> smtp.zeptomail.in)
      api_host=$(echo "${ZEPTOMAIL_API_URL:-https://api.zeptomail.in}" | sed -E 's#^[a-z]+://([^/]+).*#\1#')
      smarthost="smtp.${api_host#api.}:587"
      username=emailapikey
      password_file=$SECRETS/zeptomail-token
      ;;
    smtp)
      smarthost="${MAIL_HOST:-}:${MAIL_PORT:-587}"
      [ -n "${MAIL_HOST:-}" ] || smarthost=''
      username=${MAIL_USERNAME:-}
      [ -n "$username" ] && password_file=$SECRETS/smtp-password
      [ "${MAIL_STARTTLS:-true}" = false ] && require_tls=false
      ;;
  esac
fi

email=false
if [ -n "$smarthost" ]; then
  if [ -z "$to" ] || [ -z "$from" ]; then
    warn "email alerts need ALERT_EMAIL_TO/ADMIN_EMAIL and ALERT_EMAIL_FROM/MAIL_FROM"
  elif [ -n "$password_file" ] && ! nonempty "$password_file"; then
    warn "email alerts need the SMTP password in secrets/$(basename "$password_file")"
  elif [ "$profile" = prod ] && echo "$to" | grep -qiE '(@|\.)(example\.(com|org)|[a-z0-9-]+\.local)>?$'; then
    warn "ALERT_EMAIL_TO/ADMIN_EMAIL ($to) is a placeholder address that cannot receive mail"
  else
    email=true
  fi
fi
check "$to" ALERT_EMAIL_TO
check "$from" ALERT_EMAIL_FROM
check "$smarthost" MAIL_HOST
check "$username" MAIL_USERNAME
check "${ALERT_SLACK_CHANNEL:-}" ALERT_SLACK_CHANNEL

slack=false
nonempty "$SECRETS/alertmanager-slack-webhook" 2>/dev/null && slack=true

if [ "$email" = false ] && [ "$slack" = false ]; then
  if [ "$profile" = prod ] && [ "${ALERT_DELIVERY:-}" != none ]; then
    fail "no alert receiver can deliver: configure email (see above) or secrets/alertmanager-slack-webhook, or set ALERT_DELIVERY=none to run without notifications"
  fi
  warn "alerts are not delivered anywhere (visible in the Alertmanager UI and Grafana only)"
fi

{
  cat "$BASE"
  echo
  echo "receivers:"
  echo "  - name: default"
  if [ "$email" = true ]; then
    echo "    email_configs:"
    echo "      - to: \"$to\""
    echo "        from: \"$from\""
    echo "        smarthost: \"$smarthost\""
    echo "        require_tls: $require_tls"
    if [ -n "$username" ]; then
      echo "        auth_username: \"$username\""
      echo "        auth_password_file: $password_file"
    fi
    echo "        send_resolved: true"
  fi
  if [ "$slack" = true ]; then
    echo "    slack_configs:"
    echo "      - api_url_file: $SECRETS/alertmanager-slack-webhook"
    echo "        channel: \"${ALERT_SLACK_CHANNEL:-#the-club-alerts}\""
    echo "        send_resolved: true"
  fi
} > "$OUT"

echo "alertmanager: receivers: email=$email ($smarthost) slack=$slack"
exec /bin/alertmanager --config.file="$OUT" "$@"
