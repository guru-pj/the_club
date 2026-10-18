#!/usr/bin/env bash
# Deletes the local kind cluster created by deploy/kind/up.sh (all data in it is lost).
set -euo pipefail
CLUSTER="${KIND_CLUSTER_NAME:-theclub}"
"${KIND:-kind}" delete cluster --name "$CLUSTER"
