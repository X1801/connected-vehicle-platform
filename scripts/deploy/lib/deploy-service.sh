#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
source "$(dirname "${BASH_SOURCE[0]}")/build-image.sh"

cvp_deploy_service() {
  local service="$1" mode=apply requested_tag='' directory deployment image current other before after
  shift
  unset CVP_IMAGE_TAG
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dry-run) [[ "$mode" == apply ]] || cvp_die 'Conflicting modes'; mode=dry-run ;;
      --build) [[ "$mode" == apply ]] || cvp_die 'Conflicting modes'; mode=build ;;
      --tag) shift; [[ $# -gt 0 ]] || cvp_die '--tag requires a value'; requested_tag="$1" ;;
      *) cvp_die "Usage: deploy-$service.sh [--dry-run|--build] [--tag EXISTING_OR_NEW_TAG]" ;;
    esac
    shift
  done
  directory="$(cvp_service_dir "$service")"
  deployment="$(cvp_deployment "$service")"
  cvp_preflight
  cvp_render "$directory"
  if [[ "$mode" == dry-run ]]; then
    kubectl apply --dry-run=server -k "$directory" >/dev/null
    cvp_log "Selection: $service only ($directory)"
    return
  fi
  kubectl get statefulset cvp-postgres -n "$CVP_NAMESPACE" >/dev/null || cvp_die 'Existing PostgreSQL is required'
  kubectl get deployment cvp-kafka -n "$CVP_NAMESPACE" >/dev/null || cvp_die 'Existing Kafka is required'
  kubectl get configmap cvp-app-config -n "$CVP_NAMESPACE" >/dev/null || cvp_die 'Shared application ConfigMap is required'
  before="$(for other in "${cvp_services[@]}"; do [[ "$other" == "$service" ]] || cvp_live_image "$other"; done)"
  current="$(cvp_live_image "$service")"
  if [[ "$mode" == build ]]; then
    cvp_select_build_tag "$requested_tag" "$service"
    image="$(cvp_image "$service")"
    cvp_build_image "$service"
  elif [[ -n "$requested_tag" ]]; then
    cvp_validate_tag "$requested_tag"
    CVP_IMAGE_TAG="$requested_tag"
    export CVP_IMAGE_TAG
    image="$(cvp_image "$service")"
    [[ "$image" == "$current" ]] || cvp_node_image_exists "$image"
  else
    image="$current"
  fi
  cvp_make_render "$directory" "$service"
  trap cvp_remove_render EXIT
  kubectl apply -k "$CVP_RENDER_DIR"
  cvp_wait_service "$deployment"
  cvp_assert_image "$service" "$image"
  after="$(for other in "${cvp_services[@]}"; do [[ "$other" == "$service" ]] || cvp_live_image "$other"; done)"
  [[ "$after" == "$before" ]] || cvp_die 'An unrelated application Deployment image changed'
  cvp_remove_render
  trap - EXIT
  cvp_log "$service Ready; image $image; other six image references unchanged"
}
