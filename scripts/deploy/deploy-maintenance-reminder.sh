#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/deploy-service.sh"
cvp_deploy_service maintenance-reminder "$@"
