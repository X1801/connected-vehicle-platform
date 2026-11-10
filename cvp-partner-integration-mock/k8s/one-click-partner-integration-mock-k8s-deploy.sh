#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"; SERVICE_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"; TAG="$(date +%Y%m%d-%H%M%S)"; IMAGE="cvp-partner-integration-mock:${TAG}"; MANIFEST="$(mktemp "${TMPDIR:-/tmp}/partner-mock-${TAG}.XXXXXX.yaml")"; trap 'rm -f "$MANIFEST"' EXIT
export GRADLE_USER_HOME="$SERVICE_DIR/.gradle-user"
bash "$SERVICE_DIR/gradlew" --no-daemon -p "$SERVICE_DIR" bootJar
docker build -t "$IMAGE" "$SERVICE_DIR"
sed "s|image: cvp-partner-integration-mock:latest|image: ${IMAGE}|" "$SCRIPT_DIR/partner-integration-mock.yaml" > "$MANIFEST"
kubectl apply --dry-run=client --validate=false -f "$MANIFEST"; kubectl apply -f "$MANIFEST"; kubectl rollout status deployment/partner-integration-mock -n cvp --timeout=180s
test "$(kubectl get deployment partner-integration-mock -n cvp -o jsonpath='{.spec.template.spec.containers[0].image}')" = "$IMAGE"
test "$(kubectl get pods -n cvp -l app.kubernetes.io/name=partner-integration-mock -o jsonpath='{.items[0].spec.containers[0].image}')" = "$IMAGE"
echo "SUCCESS: $IMAGE"
