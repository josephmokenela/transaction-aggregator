#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="transaction-aggregator"
NAMESPACE="transaction-aggregator"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo "==> Creating Kind cluster..."
if kind get clusters | grep -q "^${CLUSTER_NAME}$"; then
  echo "    Cluster '${CLUSTER_NAME}' already exists, skipping creation."
else
  kind create cluster --config "${ROOT_DIR}/k8s/kind-config.yaml"
fi

echo "==> Building app image..."
docker build -t transaction-aggregator:latest "${ROOT_DIR}"

echo "==> Loading app image into Kind (bypasses Docker Hub)..."
kind load docker-image transaction-aggregator:latest --name "${CLUSTER_NAME}"

echo "==> Applying namespace..."
kubectl apply -f "${ROOT_DIR}/k8s/namespace.yaml"

echo "==> Applying PostgreSQL..."
kubectl apply -f "${ROOT_DIR}/k8s/postgres/"
kubectl rollout status statefulset/postgres -n "${NAMESPACE}" --timeout=120s

echo "==> Applying Kafka..."
kubectl apply -f "${ROOT_DIR}/k8s/kafka/"
kubectl rollout status statefulset/kafka -n "${NAMESPACE}" --timeout=120s

echo "==> Applying Vault..."
kubectl apply -f "${ROOT_DIR}/k8s/vault/deployment.yaml"
kubectl apply -f "${ROOT_DIR}/k8s/vault/service.yaml"
kubectl rollout status deployment/vault -n "${NAMESPACE}" --timeout=60s

echo "==> Seeding Vault secrets..."
# Delete any previous vault-init job so we can re-run cleanly
kubectl delete job vault-init -n "${NAMESPACE}" --ignore-not-found
kubectl apply -f "${ROOT_DIR}/k8s/vault/init-job.yaml"
kubectl wait --for=condition=complete job/vault-init -n "${NAMESPACE}" --timeout=120s
echo "    Vault secrets seeded."

echo "==> Creating Grafana ConfigMaps from local monitoring files..."
kubectl create configmap grafana-datasources \
  --from-file="${ROOT_DIR}/monitoring/grafana/provisioning/datasources/" \
  -n "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

kubectl create configmap grafana-dashboard-provider \
  --from-file="${ROOT_DIR}/monitoring/grafana/provisioning/dashboards/" \
  -n "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

kubectl create configmap grafana-dashboards \
  --from-file="${ROOT_DIR}/monitoring/grafana/dashboards/" \
  -n "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

echo "==> Applying monitoring stack..."
kubectl apply -f "${ROOT_DIR}/k8s/monitoring/prometheus/"
kubectl apply -f "${ROOT_DIR}/k8s/monitoring/grafana/"
kubectl apply -f "${ROOT_DIR}/k8s/monitoring/kafka-ui/"

echo "==> Applying app..."
kubectl apply -f "${ROOT_DIR}/k8s/app/"
kubectl rollout status deployment/transaction-aggregator -n "${NAMESPACE}" --timeout=180s

echo ""
echo "✓ Cluster is up. Services available at:"
echo "  App         → http://localhost:8080"
echo "  Swagger UI  → http://localhost:8080/swagger-ui.html"
echo "  Vault       → http://localhost:8200  (token: root)"
echo "  Kafka UI    → http://localhost:8090"
echo "  Prometheus  → http://localhost:9090"
echo "  Grafana     → http://localhost:3000  (admin / admin)"
