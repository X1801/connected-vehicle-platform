#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd -- "$SERVICE_DIR/.." && pwd)"
NAMESPACE="cvp"
IMAGE_TAG="$(date +%Y%m%d-%H%M%S)"
IMAGE="cvp-mcp-server:${IMAGE_TAG}"
MANIFEST="$(mktemp "${TMPDIR:-/tmp}/cvp-connect-mcp-${IMAGE_TAG}.XXXXXX.yaml")"
trap 'rm -f "$MANIFEST"' EXIT

cd "$PROJECT_ROOT"
echo "Deploying MCP image: $IMAGE"
"$PROJECT_ROOT/cvp-ai-assistant-service/k8s/one-click-ai-assistant-k8s-deploy.sh"
cd "$SERVICE_DIR"
./gradlew clean test bootJar --no-daemon
docker build -t "$IMAGE" .
sed -E "s|image: cvp-mcp-server:[^[:space:]]+|image: ${IMAGE}|" "$SCRIPT_DIR/mcp.yaml" > "$MANIFEST"
kubectl apply --dry-run=client --validate=false -f "$MANIFEST"
kubectl apply -f "$MANIFEST"
kubectl rollout status deployment/cvp-mcp-server -n "$NAMESPACE" --timeout=180s
kubectl wait --for=condition=ready pod -l app=cvp-mcp-server -n "$NAMESPACE" --timeout=180s
echo "SUCCESS: MCP deployed image $IMAGE"
