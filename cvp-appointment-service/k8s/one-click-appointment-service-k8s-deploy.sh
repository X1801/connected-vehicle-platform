#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd -- "$SERVICE_DIR/.." && pwd)"
NAMESPACE="cvp"
IMAGE_TAG="$(date +%Y%m%d-%H%M%S)"
APPOINTMENT_IMAGE="cvp-appointment-service:${IMAGE_TAG}"
APPOINTMENT_MANIFEST="$(mktemp "${TMPDIR:-/tmp}/appointment-service-${IMAGE_TAG}.XXXXXX.yaml")"
trap 'rm -f "$APPOINTMENT_MANIFEST"' EXIT

cd "$PROJECT_ROOT"
sed "s|image: cvp-appointment-service:latest|image: ${APPOINTMENT_IMAGE}|" \
    "$SCRIPT_DIR/appointment-service.yaml" > "$APPOINTMENT_MANIFEST"

echo "Project root: $PROJECT_ROOT"
echo "Image tag: $IMAGE_TAG"

export GRADLE_USER_HOME="$SERVICE_DIR/.gradle-user"
bash "$SERVICE_DIR/gradlew" --no-daemon -p "$SERVICE_DIR" bootJar
docker build -t "$APPOINTMENT_IMAGE" "$SERVICE_DIR"
kubectl apply --dry-run=client --validate=false -f "$APPOINTMENT_MANIFEST"
kubectl apply --dry-run=client --validate=false -f "$SCRIPT_DIR/appointment-service-ingress.yaml"
kubectl apply -f "$APPOINTMENT_MANIFEST"
kubectl apply -f "$SCRIPT_DIR/appointment-service-ingress.yaml"
kubectl rollout status deployment/appointment-service -n "$NAMESPACE" --timeout=180s

DEPLOYED_IMAGE="$(kubectl get deployment appointment-service -n "$NAMESPACE" -o jsonpath='{.spec.template.spec.containers[0].image}')"
POD_IMAGE="$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=appointment-service -o jsonpath='{.items[0].spec.containers[0].image}')"
if [[ "$DEPLOYED_IMAGE" != "$APPOINTMENT_IMAGE" || "$POD_IMAGE" != "$APPOINTMENT_IMAGE" ]]; then
    echo "ERROR: expected image $APPOINTMENT_IMAGE; deployment=$DEPLOYED_IMAGE pod=$POD_IMAGE" >&2
    exit 1
fi
for _ in {1..10}; do
    if curl -fsS -H "Host: appointment.local" http://127.0.0.1/actuator/health | grep -q '"status":"UP"'; then
        break
    fi
    sleep 2
done
curl -fsS -H "Host: appointment.local" http://127.0.0.1/actuator/health | grep -q '"status":"UP"'
echo "SUCCESS: Appointment Service deployed with image $APPOINTMENT_IMAGE"
