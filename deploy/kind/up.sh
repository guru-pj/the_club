#!/usr/bin/env bash
# ============================================================
# The Club on a local Kubernetes cluster (kind), end to end:
#   kind cluster "theclub" -> Traefik ingress -> locally built images -> secrets -> helm install (values-dev.yaml)
#
# Prerequisites: docker, kind, kubectl, helm (3.12+ or 4), and the jars built first:
#   mvn clean install -DskipTests
# Usage:
#   ./deploy/kind/up.sh                 # app at http://localhost:8088
#   KIND_HTTP_PORT=9090 ./deploy/kind/up.sh
#   SKIP_BUILD=1 ./deploy/kind/up.sh    # reuse images built earlier
#   KIND_EXTRA_VALUES=my-values.yaml ./deploy/kind/up.sh   # more chart values on top of values-dev.yaml
# Tear down: ./deploy/kind/down.sh
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER="${KIND_CLUSTER_NAME:-theclub}"
PORT="${KIND_HTTP_PORT:-8088}"
NAMESPACE="${NAMESPACE:-the-club}"
RELEASE="${RELEASE:-the-club}"
REGISTRY="theclub.local"
TAG="dev"
SECRETS_DIR="${SECRETS_DIR:-$ROOT/secrets}"
KIND="${KIND:-kind}"; KUBECTL="${KUBECTL:-kubectl}"; HELM="${HELM:-helm}"
SERVICES=(gateway-service user-service auth-service activity-service club-service event-service notification-service)

VALUES=(-f "$ROOT/deploy/helm/the-club/values-dev.yaml")
[ -n "${KIND_EXTRA_VALUES:-}" ] && VALUES+=(-f "$KIND_EXTRA_VALUES")

step() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }

# kind load docker-image fails for multi-platform images with Docker's containerd image store ("content digest
# not found"), so export just the node's platform to an archive and load that.
ARCH="$(docker version --format '{{.Server.Arch}}' 2>/dev/null || echo amd64)"
TMP_DIR="$(mktemp -d)"; trap 'rm -rf "$TMP_DIR"' EXIT
load_image() {
    local archive="$TMP_DIR/image.tar"
    docker save --platform "linux/$ARCH" -o "$archive" "$1" 2>/dev/null || docker save -o "$archive" "$1"
    "$KIND" load image-archive --name "$CLUSTER" "$archive" >/dev/null
    rm -f "$archive"
}
need() { command -v "$1" >/dev/null 2>&1 || { echo "❌ '$1' not found — see docs/KUBERNETES.md (prerequisites)" >&2; exit 1; }; }
need docker; need "$KIND"; need "$KUBECTL"; need "$HELM"

if [ -z "${SKIP_BUILD:-}" ]; then
    for svc in "${SERVICES[@]}"; do
        ls "$ROOT/$svc"/target/*.jar >/dev/null 2>&1 || { echo "❌ $svc jar missing: run 'mvn clean install -DskipTests' first" >&2; exit 1; }
    done
fi

if [ -z "${SKIP_BUILD:-}" ]; then
    step "Building images (from the jars in */target and web-app/)"
    for svc in "${SERVICES[@]}"; do
        docker build -q -t "$REGISTRY/the-club-$svc:$TAG" "$ROOT/$svc" >/dev/null && echo "  $svc"
    done
    docker build -q -t "$REGISTRY/the-club-web-app:$TAG" "$ROOT/web-app" >/dev/null && echo "  web-app"
fi

step "kind cluster '$CLUSTER' (ingress on 127.0.0.1:$PORT)"
if "$KIND" get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    echo "already exists"
else
    sed -e "s/hostPort: 8088 .*/hostPort: $PORT/" -e "s/^name: theclub$/name: $CLUSTER/" \
        "$ROOT/deploy/kind/cluster.yaml" | "$KIND" create cluster --config=- --wait 120s
fi
"$KUBECTL" config use-context "kind-$CLUSTER" >/dev/null

TRAEFIK_ARGS=(--set service.type=NodePort --set ports.web.nodePort=30080
              --set ingressClass.isDefaultClass=true --set deployment.replicas=1)
"$HELM" repo add traefik https://traefik.github.io/charts >/dev/null 2>&1 || true
"$HELM" repo update traefik >/dev/null

# Pull third-party images on the host and load them into the node: faster, and works where the kind node
# cannot reach registries itself (corporate proxies, air-gapped machines).
step "Preloading third-party images"
images=$( { "$HELM" template traefik traefik/traefik "${TRAEFIK_ARGS[@]}";
            "$HELM" template "$RELEASE" "$ROOT/deploy/helm/the-club" "${VALUES[@]}"; } \
          | sed -n 's/^ *image: *"\{0,1\}\([^" ]*\)"\{0,1\}.*/\1/p' | grep -v "^$REGISTRY/" | sort -u)
for image in $images; do
    docker image inspect "$image" >/dev/null 2>&1 || docker pull -q --platform "linux/$ARCH" "$image" >/dev/null
    load_image "$image" && echo "  $image"
done

step "Traefik ingress controller"
"$HELM" upgrade --install traefik traefik/traefik -n traefik --create-namespace --wait "${TRAEFIK_ARGS[@]}" >/dev/null
echo "ready"

step "Loading images into the cluster"
for image in "${SERVICES[@]}" web-app; do
    load_image "$REGISTRY/the-club-$image:$TAG" && echo "  $image"
done

step "Secrets"
if [ ! -f "$SECRETS_DIR/theclub.jwt.private-key" ]; then
    SECRETS_DIR="$SECRETS_DIR" "$ROOT/scripts/generate-secrets.sh"
fi
"$KUBECTL" create namespace "$NAMESPACE" --dry-run=client -o yaml | "$KUBECTL" apply -f - >/dev/null
# one key per file of $SECRETS_DIR, the same names production uses
"$KUBECTL" -n "$NAMESPACE" create secret generic "$RELEASE-secrets" --from-file="$SECRETS_DIR" \
    --dry-run=client -o yaml | "$KUBECTL" apply -f - >/dev/null
echo "secret $RELEASE-secrets from $SECRETS_DIR"

step "Installing the chart (first start runs database migrations; a few minutes)"
"$HELM" upgrade --install "$RELEASE" "$ROOT/deploy/helm/the-club" -n "$NAMESPACE" \
    "${VALUES[@]}" \
    --set config.appBaseUrl="http://localhost:$PORT" \
    --set config.corsAllowedOrigins="http://localhost:$PORT" \
    --set config.strava.redirectUri="http://localhost:$PORT/strava/callback" \
    --wait --timeout 15m

"$KUBECTL" -n "$NAMESPACE" get pods
cat <<DONE

✅ The Club is running on kind.
   App:     http://localhost:$PORT
   Admin:   admin@theclub.local / $(cat "$SECRETS_DIR/admin-password")
   Emails:  kubectl -n $NAMESPACE port-forward svc/$RELEASE-mailpit 8025:8025   -> http://localhost:8025
   Smoke:   kubectl -n $NAMESPACE port-forward svc/$RELEASE-gateway 18080:8080 &
            GATEWAY_URL=http://localhost:18080 STRAVA_WEBHOOK_VERIFY_TOKEN=dev-strava-verify-token \\
              ADMIN_PASSWORD=\$(cat $SECRETS_DIR/admin-password) ./scripts/smoke-test.sh
   Remove:  ./deploy/kind/down.sh
DONE
