#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
NAMESPACE="cvp"
IMAGE_TAG="$(date +%Y%m%d-%H%M%S)"
IMAGE="cvp-telemetry-service:${IMAGE_TAG}"
MANIFEST="$(mktemp "${TMPDIR:-/tmp}/telemetry-${IMAGE_TAG}.XXXXXX.yaml")"
trap 'rm -f "$MANIFEST"' EXIT

sed "s|image: cvp-telemetry-service:latest|image: ${IMAGE}|" "$SCRIPT_DIR/telemetry-service.yaml" > "$MANIFEST"
export GRADLE_USER_HOME="$SERVICE_DIR/.gradle-user"
bash "$SERVICE_DIR/gradlew" --no-daemon -p "$SERVICE_DIR" test bootJar
docker build -t "$IMAGE" "$SERVICE_DIR"
kubectl apply --dry-run=client --validate=false -f "$MANIFEST"
kubectl apply --dry-run=client --validate=false -f "$SCRIPT_DIR/telemetry-service-ingress.yaml"
kubectl apply -f "$MANIFEST"
kubectl apply -f "$SCRIPT_DIR/telemetry-service-ingress.yaml"
kubectl rollout status deployment/cvp-telemetry-service -n "$NAMESPACE" --timeout=180s

DEPLOYED_IMAGE="$(kubectl get deployment cvp-telemetry-service -n "$NAMESPACE" -o jsonpath='{.spec.template.spec.containers[0].image}')"
POD_IMAGE="$(kubectl get pods -n "$NAMESPACE" -l app=cvp-telemetry-service -o jsonpath='{.items[0].spec.containers[0].image}')"
[[ "$DEPLOYED_IMAGE" == "$IMAGE" && "$POD_IMAGE" == "$IMAGE" ]]
for _ in {1..10}; do curl -fsS -H 'Host: telemetry.local' http://127.0.0.1/actuator/health | grep -q '"status":"UP"' && break; sleep 2; done
curl -fsS -H 'Host: telemetry.local' http://127.0.0.1/actuator/health | grep -q '"status":"UP"'
echo "SUCCESS: Telemetry Service deployed with image $IMAGE"
