#!/usr/bin/env bash
# Focused image-selection checks; all external commands are mocked.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

count_file="$(mktemp "$CVP_ROOT/scripts/deploy/.tag-test-XXXXXXXX")"
trap 'rm -f -- "$count_file"' EXIT

date() {
  [[ "$*" == '-u +%Y%m%d-%H%M%S' ]] || return 1
  printf 'x\n' >> "$count_file"
  printf '20261107-101112\n'
}

docker() {
  [[ "$1" == image && "$2" == inspect ]] || return 1
  [[ "$3" == *:duplicate ]]
}

kubectl() {
  [[ "$1" == get && "$2" == deployment/* ]] || return 1
  case "$2" in
    deployment/ai-assistant-service) printf 'docker.io/library/cvp-ai-assistant-service:old-ai' ;;
    deployment/appointment-service) printf 'docker.io/library/cvp-appointment-service:old-appointment' ;;
    deployment/maintenance-reminder-service) printf 'docker.io/library/cvp-maintenance-reminder-service:old-maintenance' ;;
    deployment/partner-integration-mock) printf 'docker.io/library/cvp-partner-integration-mock:old-partner' ;;
    deployment/inventory-service) printf 'docker.io/library/cvp-inventory-service:old-inventory' ;;
    deployment/mcp-server) printf 'docker.io/library/cvp-mcp-server:old-mcp' ;;
    deployment/telemetry-service) printf 'docker.io/library/cvp-telemetry-service:old-telemetry' ;;
    *) return 1 ;;
  esac
}

unset CVP_IMAGE_TAG
[[ "$(cvp_image inventory)" == 'docker.io/library/cvp-inventory-service:old-inventory' ]]
[[ "$(cvp_image telemetry)" == 'docker.io/library/cvp-telemetry-service:old-telemetry' ]]
[[ ! -s "$count_file" ]]

cvp_select_build_tag '' "${cvp_services[@]}"
[[ "$CVP_IMAGE_TAG" == 20261107-101112 ]]
[[ "$(wc -l < "$count_file")" -eq 1 ]]
for service in "${cvp_services[@]}"; do
  [[ "$(cvp_image "$service")" == "$(cvp_image_name "$service"):20261107-101112" ]]
done

: > "$count_file"
cvp_select_build_tag '' inventory
[[ "$(wc -l < "$count_file")" -eq 1 ]]
[[ "$(cvp_image inventory)" == 'docker.io/library/cvp-inventory-service:20261107-101112' ]]
[[ "$(cvp_live_image appointment)" == 'docker.io/library/cvp-appointment-service:old-appointment' ]]

if (cvp_select_build_tag 'invalid/tag' inventory) >/dev/null 2>&1; then cvp_die 'Invalid tag was accepted'; fi
if (cvp_select_build_tag duplicate inventory) >/dev/null 2>&1; then cvp_die 'Existing tag was accepted'; fi
cvp_log 'Image tag selection tests passed: shared build, individual build, reuse, syntax, collision'
