#!/usr/bin/env bash
# Explicit opt-in build. Work happens in a temporary copy under scripts/deploy,
# so service-local source and build files remain untouched.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

cvp_build_cleanup() {
  if [[ -n "${CVP_BUILD_HELPER:-}" ]]; then
    kubectl delete pod "$CVP_BUILD_HELPER" -n "$CVP_NAMESPACE" --ignore-not-found --wait=true >/dev/null 2>&1 || true
    CVP_BUILD_HELPER=''
  fi
  if [[ -n "${CVP_BUILD_WORK_DIR:-}" ]]; then
    [[ "$CVP_BUILD_WORK_DIR" == "$CVP_ROOT/scripts/deploy/.build-"* ]] || cvp_die 'Refusing to remove an unexpected build path'
    rm -rf -- "$CVP_BUILD_WORK_DIR"
    CVP_BUILD_WORK_DIR=''
  fi
}

cvp_build_image() {
  local service="$1" source_dir image work_dir node helper
  source_dir="$(cvp_source_dir "$service")"
  image="$(cvp_image "$service")"
  cvp_require docker
  cvp_require tar
  [[ -f "$source_dir/Dockerfile" ]] || cvp_die "Missing Dockerfile: $source_dir"
  work_dir="$(mktemp -d "$CVP_ROOT/scripts/deploy/.build-XXXXXXXX")"
  helper="cvp-image-import-$$"
  CVP_BUILD_WORK_DIR="$work_dir"
  CVP_BUILD_HELPER="$helper"
  trap cvp_build_cleanup EXIT
  tar -C "$source_dir" --exclude='./build' --exclude='./target' --exclude='./.gradle' --exclude='./.gradle-user' --exclude='./.m2' -cf - . | tar -C "$work_dir" -xf -
  if [[ "$service" == ai-assistant || "$service" == maintenance-reminder ]]; then
    # The separate prefetch goal can stall on optional plugin dependencies.
    # Maven's package goal still resolves everything needed for the actual JAR.
    sed -i '/dependency:go-offline/d' "$work_dir/Dockerfile"
    # Keep successful downloads if a transient Maven Central transfer fails.
    sed -i 's|^RUN ./mvnw |RUN --mount=type=cache,target=/root/.m2 ./mvnw |' "$work_dir/Dockerfile"
  fi
  if [[ "$service" == ai-assistant ]]; then
    # The live AI image contains the root knowledge fixture at /app/knowledge.
    # The service-local Dockerfile omits it, so amend only this disposable copy.
    cp -R "$CVP_ROOT/knowledge" "$work_dir/knowledge"
    sed -i '/^EXPOSE 8080$/i COPY --chown=assistant:assistant knowledge /app/knowledge' "$work_dir/Dockerfile"
  fi
  case "$service" in
    ai-assistant|maintenance-reminder) : ;; # Their original Dockerfiles build with Maven.
    appointment|partner-mock)
      env -u MSYS_NO_PATHCONV GRADLE_USER_HOME="$CVP_ROOT/scripts/deploy/.gradle-cache" bash "$work_dir/gradlew" --no-daemon -p "$work_dir" bootJar ;;
    inventory|telemetry)
      env -u MSYS_NO_PATHCONV GRADLE_USER_HOME="$CVP_ROOT/scripts/deploy/.gradle-cache" bash "$work_dir/gradlew" --no-daemon -p "$work_dir" test bootJar ;;
    mcp-server)
      env -u MSYS_NO_PATHCONV GRADLE_USER_HOME="$CVP_ROOT/scripts/deploy/.gradle-cache" bash "$work_dir/gradlew" --no-daemon -p "$work_dir" clean test bootJar ;;
  esac
  cvp_log "Building $image"
  docker build -t "$image" "$work_dir"
  node="$(kubectl get nodes -o jsonpath='{.items[0].metadata.name}')"
  [[ -n "$node" ]] || cvp_die 'No Kubernetes node found for containerd import'
  # The 2026-11-04 deployment verified node-local ctr import in namespace k8s.io.
  # This temporary helper mounts the node root and is removed by the trap.
  cat <<YAML | kubectl apply -f - >/dev/null
apiVersion: v1
kind: Pod
metadata:
  name: $helper
  namespace: $CVP_NAMESPACE
  labels: { app.kubernetes.io/part-of: cvp-platform, app.kubernetes.io/managed-by: cvp-image-import }
spec:
  nodeName: $node
  restartPolicy: Never
  containers:
    - name: importer
      image: docker.io/library/postgres:16-alpine
      imagePullPolicy: IfNotPresent
      command: ["/bin/sh", "-c", "sleep 600"]
      securityContext: { privileged: true }
      volumeMounts: [{ name: host, mountPath: /host }]
  volumes: [{ name: host, hostPath: { path: /, type: Directory } }]
YAML
  kubectl wait pod "$helper" -n "$CVP_NAMESPACE" --for=condition=Ready --timeout=120s
  # The node may retain an older image even after it was removed from Docker.
  if kubectl exec -n "$CVP_NAMESPACE" "$helper" -- chroot /host /bin/sh -c "ctr -n k8s.io images ls -q | grep -Fx '$image'" >/dev/null 2>&1; then
    cvp_die "Refusing to overwrite existing node image $image"
  fi
  cvp_log "Importing $image into $node containerd namespace k8s.io"
  docker image save "$image" | kubectl exec -i -n "$CVP_NAMESPACE" "$helper" -- chroot /host /bin/sh -c 'ctr -n k8s.io images import --platform linux/amd64 -'
  kubectl exec -n "$CVP_NAMESPACE" "$helper" -- chroot /host /bin/sh -c "ctr -n k8s.io images ls -q | grep -Fx '$image'" >/dev/null || cvp_die "Node import missing $image"
  cvp_build_cleanup
  trap - EXIT
}

# Used for explicit existing-tag deployments. A running Pod is sufficient proof
# for the default no-build path; this checks images not currently running.
cvp_node_image_exists() {
  local image="$1" node helper
  node="$(kubectl get nodes -o jsonpath='{.items[0].metadata.name}')"
  helper="cvp-image-check-$$"
  cat <<YAML | kubectl apply -f - >/dev/null
apiVersion: v1
kind: Pod
metadata:
  name: $helper
  namespace: $CVP_NAMESPACE
  labels: { app.kubernetes.io/part-of: cvp-platform, app.kubernetes.io/managed-by: cvp-image-check }
spec:
  nodeName: $node
  restartPolicy: Never
  containers:
    - name: checker
      image: docker.io/library/postgres:16-alpine
      imagePullPolicy: IfNotPresent
      command: ["/bin/sh", "-c", "sleep 600"]
      securityContext: { privileged: true }
      volumeMounts: [{ name: host, mountPath: /host }]
  volumes: [{ name: host, hostPath: { path: /, type: Directory } }]
YAML
  if ! kubectl wait pod "$helper" -n "$CVP_NAMESPACE" --for=condition=Ready --timeout=120s; then
    kubectl delete pod "$helper" -n "$CVP_NAMESPACE" --ignore-not-found --wait=true >/dev/null
    cvp_die "Image-check helper $helper did not become Ready"
  fi
  if ! kubectl exec -n "$CVP_NAMESPACE" "$helper" -- chroot /host /bin/sh -c "ctr -n k8s.io images ls -q | grep -Fx '$image'" >/dev/null; then
    kubectl delete pod "$helper" -n "$CVP_NAMESPACE" --wait=true >/dev/null
    cvp_die "Selected image is unavailable to the Kubernetes node: $image"
  fi
  kubectl delete pod "$helper" -n "$CVP_NAMESPACE" --wait=true >/dev/null
}
