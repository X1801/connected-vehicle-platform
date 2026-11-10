#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cvp_preflight
cvp_require curl
cvp_require grep

ingress_ip="$(kubectl get service ingress-nginx-controller -n ingress-nginx -o jsonpath='{.status.loadBalancer.ingress[0].ip}')"
[[ -n "$ingress_ip" ]] || cvp_die 'Existing NGINX Ingress controller has no LoadBalancer IP'
if ! curl --noproxy '*' --max-time 3 --silent "http://$ingress_ip/" >/dev/null; then
  # Docker Desktop exposes this same controller on Windows loopback even when
  # the internal LoadBalancer IP is not directly routed to the Windows host.
  curl --noproxy '*' --max-time 3 --silent http://127.0.0.1/ >/dev/null || cvp_die 'NGINX Ingress controller is unreachable from this host'
  ingress_ip=127.0.0.1
fi
cvp_log "Ingress controller test address: $ingress_ip"
tmp_dir="$(mktemp -d "$CVP_ROOT/scripts/deploy/.verify-XXXXXXXX")"
cvp_verify_cleanup() {
  [[ "$tmp_dir" == "$CVP_ROOT/scripts/deploy/.verify-"* ]] || cvp_die 'Refusing to remove an unexpected verification path'
  rm -rf -- "$tmp_dir"
}
trap cvp_verify_cleanup EXIT

cvp_get() {
  local host="$1" path="$2" output="$3" status
  status="$(curl --noproxy '*' --resolve "$host:80:$ingress_ip" --max-time 30 --silent --show-error --location --output "$output" --write-out '%{http_code}' "http://$host$path")"
  [[ "$status" == 200 ]] || cvp_die "HTTP $status: http://$host$path"
}

kubectl rollout status statefulset/cvp-postgres -n "$CVP_NAMESPACE" --timeout="$CVP_TIMEOUT" >/dev/null
kubectl exec -n "$CVP_NAMESPACE" cvp-postgres-0 -- pg_isready -U cvp_test -d postgres >/dev/null
[[ "$(kubectl get pvc cvp-postgres-data -n "$CVP_NAMESPACE" -o jsonpath='{.status.phase}')" == Bound ]] || cvp_die 'PostgreSQL PVC is not Bound'
kubectl rollout status deployment/cvp-kafka -n "$CVP_NAMESPACE" --timeout="$CVP_TIMEOUT" >/dev/null
kubectl exec -n "$CVP_NAMESPACE" deployment/cvp-kafka -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list >/dev/null
cvp_log 'PostgreSQL, PVC, and Kafka: healthy'

while IFS='|' read -r service deployment ingress host path; do
  [[ -n "$service" ]] || continue
  cvp_wait_service "$deployment" >/dev/null
  actual_host="$(kubectl get ingress "$ingress" -n "$CVP_NAMESPACE" -o jsonpath='{.spec.rules[0].host}')"
  actual_service="$(kubectl get ingress "$ingress" -n "$CVP_NAMESPACE" -o jsonpath='{.spec.rules[0].http.paths[0].backend.service.name}')"
  actual_path="$(kubectl get ingress "$ingress" -n "$CVP_NAMESPACE" -o jsonpath='{.spec.rules[0].http.paths[0].path}')"
  actual_class="$(kubectl get ingress "$ingress" -n "$CVP_NAMESPACE" -o jsonpath='{.spec.ingressClassName}')"
  [[ "$actual_host" == "$host" && "$actual_service" == "$deployment" && "$actual_path" == "$path" && "$actual_class" == nginx ]] || cvp_die "Ingress $ingress routes incorrectly"
  if [[ "$service" != mcp-server ]]; then
    cvp_get "$host" /actuator/health "$tmp_dir/health.json"
    grep -Fq 'UP' "$tmp_dir/health.json" || cvp_die "Ingress health body is unexpected for $service"
  fi
  cvp_log "$service: Ready; Ingress http://$host$path -> $deployment"
done <<'SERVICES'
ai-assistant|ai-assistant-service|ai-assistant-ingress|assistant.local|/
appointment|appointment-service|appointment-service-ingress|appointment.local|/
maintenance-reminder|maintenance-reminder-service|maintenance-reminder-ingress|maintenance-reminder.local|/
partner-mock|partner-integration-mock|partner-integration-mock-ingress|partner-mock.local|/
inventory|inventory-service|inventory-service-ingress|inventory.local|/
mcp-server|mcp-server|mcp-server-ingress|mcp.local|/mcp
telemetry|telemetry-service|cvp-telemetry-service-ingress|telemetry.local|/
SERVICES

while IFS='|' read -r service host marker; do
  [[ -n "$service" ]] || continue
  cvp_get "$host" /swagger-ui/index.html "$tmp_dir/swagger.html"
  grep -Fq 'Swagger UI' "$tmp_dir/swagger.html" || cvp_die "Unexpected Swagger UI content for $service"
  cvp_get "$host" /v3/api-docs "$tmp_dir/openapi.json"
  grep -Fq "$marker" "$tmp_dir/openapi.json" || cvp_die "OpenAPI document for $service lacks $marker"
  cvp_get "$host" /v3/api-docs/swagger-config "$tmp_dir/swagger-config.json"
  grep -Fq '/v3/api-docs' "$tmp_dir/swagger-config.json" || cvp_die "Swagger UI cannot locate OpenAPI for $service"
  cvp_log "$service Swagger UI: http://$host/swagger-ui/index.html (200); OpenAPI: http://$host/v3/api-docs (200)"
done <<'REST_SERVICES'
ai-assistant|assistant.local|/api/v1/assistant/chat
appointment|appointment.local|/api/v1/appointments
maintenance-reminder|maintenance-reminder.local|/api/v1/vehicles/
partner-mock|partner-mock.local|/mock/v1/bookings
inventory|inventory.local|/v1/vehicles/
telemetry|telemetry.local|/v1/vehicles/
REST_SERVICES

mcp_url='http://mcp.local/mcp'
mcp_headers=(-H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream')
mcp_status="$(curl --noproxy '*' --resolve "mcp.local:80:$ingress_ip" --max-time 30 --silent --show-error --output "$tmp_dir/mcp-init.json" --dump-header "$tmp_dir/mcp-headers.txt" --write-out '%{http_code}' -X POST "${mcp_headers[@]}" --data '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"cvp-deployment-verify","version":"1.0"}}}' "$mcp_url")"
[[ "$mcp_status" == 200 ]] || cvp_die "MCP initialize returned HTTP $mcp_status"
grep -Fq 'result' "$tmp_dir/mcp-init.json" || cvp_die 'MCP initialize response lacks a JSON-RPC result'
session_id="$(tr -d '\r' < "$tmp_dir/mcp-headers.txt" | awk 'tolower($1)=="mcp-session-id:" { print $2; exit }')"
if [[ -n "$session_id" ]]; then mcp_headers+=(-H "Mcp-Session-Id: $session_id"); fi
curl --noproxy '*' --resolve "mcp.local:80:$ingress_ip" --max-time 30 --silent --show-error --output "$tmp_dir/mcp-initialized.json" -X POST "${mcp_headers[@]}" --data '{"jsonrpc":"2.0","method":"notifications/initialized"}' "$mcp_url"
mcp_status="$(curl --noproxy '*' --resolve "mcp.local:80:$ingress_ip" --max-time 30 --silent --show-error --output "$tmp_dir/mcp-tools.json" --write-out '%{http_code}' -X POST "${mcp_headers[@]}" --data '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' "$mcp_url")"
[[ "$mcp_status" == 200 ]] || cvp_die "MCP tools/list returned HTTP $mcp_status"
grep -Fq 'getVehicleCapability' "$tmp_dir/mcp-tools.json" || cvp_die 'MCP tools/list did not discover the Inventory tool'
cvp_log "$mcp_url: initialize and tools/list succeeded through NGINX Ingress"
cvp_log 'All seven Deployments, Services, Ingress routes, six Swagger/OpenAPI pairs, and MCP transport verified'
