#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
source "$(dirname "${BASH_SOURCE[0]}")/lib/build-image.sh"
unset CVP_IMAGE_TAG

# Default: reapply the seven live image references without a build or restart.
# --build: one UTC release tag for seven newly built and imported images.
# --tag TAG: select an existing tag for reuse, or choose a new tag with --build.
mode=apply
requested_tag=''
allow_new_infrastructure=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) [[ "$mode" == apply ]] || cvp_die 'Conflicting modes'; mode=dry-run ;;
    --build) [[ "$mode" == apply ]] || cvp_die 'Conflicting modes'; mode=build ;;
    --tag) shift; [[ $# -gt 0 ]] || cvp_die '--tag requires a value'; requested_tag="$1" ;;
    --allow-new-infrastructure) allow_new_infrastructure=true ;;
    *) cvp_die 'Usage: deploy-all.sh [--dry-run|--build] [--tag EXISTING_OR_NEW_TAG] [--allow-new-infrastructure]' ;;
  esac
  shift
done

cvp_preflight
kubectl get ingressclass nginx >/dev/null || cvp_die 'Existing nginx IngressClass is required'
cvp_render "$CVP_ROOT/k8s/base/infrastructure"
for service in "${cvp_services[@]}"; do cvp_render "$(cvp_service_dir "$service")"; done
cvp_render "$CVP_LOCAL_OVERLAY"
if [[ "$mode" == dry-run ]]; then
  kubectl apply --dry-run=server -k "$CVP_LOCAL_OVERLAY" >/dev/null
  cvp_log 'Full local overlay passed server dry-run; no resources applied'
  exit 0
fi

if kubectl get pvc cvp-postgres-data -n "$CVP_NAMESPACE" >/dev/null 2>&1; then
  [[ "$(kubectl get pvc cvp-postgres-data -n "$CVP_NAMESPACE" -o jsonpath='{.status.phase}')" == Bound ]] || cvp_die 'Existing PostgreSQL PVC is not Bound'
elif [[ "$allow_new_infrastructure" != true ]]; then
  cvp_die 'PostgreSQL PVC is missing; pass --allow-new-infrastructure only for a deliberate fresh installation'
fi

declare -A previous_images expected_images
for service in "${cvp_services[@]}"; do previous_images[$service]="$(cvp_live_image "$service")"; done

if [[ "$mode" == build ]]; then
  cvp_select_build_tag "$requested_tag" "${cvp_services[@]}"
  cvp_log "Full release tag: $CVP_IMAGE_TAG"
  for service in "${cvp_services[@]}"; do
    expected_images[$service]="$(cvp_image "$service")"
    cvp_build_image "$service"
  done
elif [[ -n "$requested_tag" ]]; then
  cvp_validate_tag "$requested_tag"
  CVP_IMAGE_TAG="$requested_tag"
  export CVP_IMAGE_TAG
  for service in "${cvp_services[@]}"; do
    expected_images[$service]="$(cvp_image "$service")"
    [[ "${expected_images[$service]}" == "${previous_images[$service]}" ]] || cvp_node_image_exists "${expected_images[$service]}"
  done
else
  for service in "${cvp_services[@]}"; do expected_images[$service]="${previous_images[$service]}"; done
fi

cvp_make_render "$CVP_LOCAL_OVERLAY" "${cvp_services[@]}"
trap cvp_remove_render EXIT
kubectl apply --dry-run=server -k "$CVP_RENDER_DIR" >/dev/null
# This composition owns the existing resource names; no prune or delete is used.
kubectl apply -k "$CVP_RENDER_DIR"
kubectl rollout status statefulset/cvp-postgres -n "$CVP_NAMESPACE" --timeout="$CVP_TIMEOUT"
for dependency in cvp-kafka cvp-notification-mock; do
  kubectl rollout status "deployment/$dependency" -n "$CVP_NAMESPACE" --timeout="$CVP_TIMEOUT"
done
for service in "${cvp_services[@]}"; do
  deployment="$(cvp_deployment "$service")"
  cvp_wait_service "$deployment"
  cvp_assert_image "$service" "${expected_images[$service]}"
  if [[ "$mode" == apply && -z "$requested_tag" ]]; then
    [[ "$(cvp_live_image "$service")" == "${previous_images[$service]}" ]] || cvp_die "No-build changed $service image"
  fi
done
cvp_remove_render
trap - EXIT
bash "$(dirname "${BASH_SOURCE[0]}")/verify-all.sh"
if [[ "$mode" == build ]]; then
  cvp_log "Unified deployment complete; shared release tag $CVP_IMAGE_TAG; seven application Deployments Ready"
else
  cvp_log 'Unified no-build deployment complete; seven application image references retained or selected as requested'
fi
