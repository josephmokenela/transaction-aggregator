#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="transaction-aggregator"

echo "==> Deleting Kind cluster '${CLUSTER_NAME}'..."
kind delete cluster --name "${CLUSTER_NAME}"
echo "✓ Cluster deleted. Docker Compose setup is unaffected."
