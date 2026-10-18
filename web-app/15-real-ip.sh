#!/bin/sh
# Trust X-Forwarded-For only from the given proxy address ranges (REAL_IP_FROM, comma-separated CIDRs), e.g. a
# TLS proxy such as Caddy on the same host (Docker bridge: 172.16.0.0/12). Without this, every client appears to
# come from the proxy and all clients share one gateway rate-limit bucket. Unset = nothing is trusted.
# (Kubernetes mounts its own snippets/real-ip.conf instead and leaves REAL_IP_FROM unset.)
set -eu
[ -z "${REAL_IP_FROM:-}" ] && exit 0
out=/etc/nginx/snippets/real-ip-env.conf
{
    for cidr in $(echo "$REAL_IP_FROM" | tr ',' ' '); do
        case "$cidr" in
            *[!0-9a-fA-F:./]*) echo "15-real-ip.sh: invalid CIDR '$cidr'" >&2; exit 1 ;;
        esac
        echo "set_real_ip_from $cidr;"
    done
    echo "real_ip_header X-Forwarded-For;"
    echo "real_ip_recursive on;"
} > "$out"
echo "15-real-ip.sh: trusting X-Forwarded-For from $REAL_IP_FROM"
