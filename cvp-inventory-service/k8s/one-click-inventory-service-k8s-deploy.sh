#!/bin/bash
set -euo pipefail
DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"; TAG="$(date +%Y%m%d-%H%M%S)"; IMAGE="cvp-inventory-service:${TAG}"; TMP="$(mktemp)"; trap 'rm -f "$TMP"' EXIT
export GRADLE_USER_HOME="$DIR/.gradle-user"; bash "$DIR/gradlew" --no-daemon -p "$DIR" test bootJar; docker build -t "$IMAGE" "$DIR"
sed "s|image: cvp-inventory-service:latest|image: $IMAGE|" "$DIR/k8s/inventory-service.yaml" > "$TMP"
kubectl apply -f "$TMP"; kubectl apply -f "$DIR/k8s/inventory-service-ingress.yaml"; kubectl rollout status deployment/cvp-inventory-service -n cvp --timeout=180s
