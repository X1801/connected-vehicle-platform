#!/bin/bash
set -euo pipefail

: "${ASSISTANT_DB_PASSWORD:?Set ASSISTANT_DB_PASSWORD before deployment}"
DEEPSEEK_API_KEY="${DEEPSEEK_API_KEY:-}"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd -- "$SERVICE_DIR/.." && pwd)"
NAMESPACE="cvp"
IMAGE_TAG="$(date +%Y%m%d-%H%M%S)"
ASSISTANT_IMAGE="cvp-ai-assistant-service:${IMAGE_TAG}"
ASSISTANT_MANIFEST="$(mktemp "${TMPDIR:-/tmp}/cvp-ai-assistant-${IMAGE_TAG}.XXXXXX.yaml")"
trap 'rm -f "$ASSISTANT_MANIFEST"' EXIT

cd "$PROJECT_ROOT"

sed "s|image: cvp-ai-assistant-service:latest|image: ${ASSISTANT_IMAGE}|" \
    "$SCRIPT_DIR/04-assistant.yaml" > "$ASSISTANT_MANIFEST"

echo "Project root: $PROJECT_ROOT"
echo "Image tag: $IMAGE_TAG"

echo "========================================"
echo "1. Cleaning AI Assistant resources..."
echo "========================================"

kubectl delete -f "$SCRIPT_DIR/05-ingress.yaml" --ignore-not-found=true --wait=true
kubectl delete -f "$ASSISTANT_MANIFEST" --ignore-not-found=true --wait=true
kubectl delete -f "$SCRIPT_DIR/02-configmap.yaml" --ignore-not-found=true --wait=true

echo "========================================"
echo "2. Building Docker images..."
echo "========================================"

docker build -t "$ASSISTANT_IMAGE" "$SERVICE_DIR"

echo "========================================"
echo "3. Validating Kubernetes manifests..."
echo "========================================"

kubectl apply --dry-run=client --validate=false -f "$SCRIPT_DIR/02-configmap.yaml"
kubectl apply --dry-run=client --validate=false -f "$ASSISTANT_MANIFEST"
kubectl apply --dry-run=client --validate=false -f "$SCRIPT_DIR/05-ingress.yaml"

echo "========================================"
echo "4. Deploying Kubernetes resources..."
echo "========================================"

kubectl create secret generic cvp-ai-assistant-secrets -n "$NAMESPACE" \
    --from-literal=DEEPSEEK_API_KEY="$DEEPSEEK_API_KEY" \
    --from-literal=ASSISTANT_DB_PASSWORD="$ASSISTANT_DB_PASSWORD" \
    --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f "$SCRIPT_DIR/02-configmap.yaml"
kubectl apply -f "$ASSISTANT_MANIFEST"

echo "========================================"
echo "5. Waiting for deployments..."
echo "========================================"

kubectl rollout status deployment/cvp-ai-assistant -n "$NAMESPACE" --timeout=180s

echo "========================================"
echo "6. Checking Spring Boot startup..."
echo "========================================"

if ! kubectl logs deployment/cvp-ai-assistant -n "$NAMESPACE" | grep -q "Started AssistantApplication in"; then
    echo "ERROR: AI Assistant startup failed."
    exit 1
fi

echo "AI Assistant started successfully."

echo "========================================"
echo "7. Applying Ingress..."
echo "========================================"

kubectl apply -f "$SCRIPT_DIR/05-ingress.yaml"

echo "========================================"
echo "8. Checking /actuator/health through Ingress..."
echo "========================================"

echo "Waiting for Ingress..."
for i in {1..10}; do
    if curl -s -H "Host: assistant.local" \
        http://127.0.0.1/actuator/health | grep -q '"status":"UP"'; then
        break
    fi
    sleep 2
done

HEALTH=$(curl -s -H "Host: assistant.local" \
    http://127.0.0.1/actuator/health)

echo "Health response: $HEALTH"

if echo "$HEALTH" | grep -q '"status":"UP"'; then
    echo ""
    echo "========================================"
    echo "SUCCESS: AI Assistant deployment completed."
    echo "Spring Boot: STARTED"
    echo "Health: UP"
    echo "URL: http://assistant.local"
    echo "========================================"
else
    echo "ERROR: AI Assistant health check failed."
    exit 1
fi
