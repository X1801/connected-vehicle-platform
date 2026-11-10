# AI Assistant local Kubernetes deployment

These manifests deploy the Phase 1 Assistant into the existing `Maintenance Reminder Service` namespace. Inventory is provided independently by `cvp-inventory-service`. They reuse the existing PostgreSQL server, creating a separate `ai_assistant` database and role without changing the Maintenance Reminder Service database/schema.

## Prerequisites

- Java 21 and Maven for local builds.
- Docker, with Docker Desktop Kubernetes enabled if using the local cluster.
- `kubectl`, pointed at the intended cluster: `kubectl config current-context`.
- The Maintenance Reminder Service and Telemetry deployments/services must already be available in namespace `cvp` when the Assistant needs maintenance evidence.

## Build images

From the repository root:

The one-click script generates a timestamp tag and injects it into the Assistant manifest:

```bash
./k8s/one-click-ai-assistant-k8s-deploy.sh
```

The checked-in manifests intentionally keep the readable `:latest` placeholders; the script never rewrites them.

Docker Desktop Kubernetes usually shares Docker's image store. For another cluster, load the Assistant image using that cluster's documented local-image mechanism; do not publish credentials or images to a public registry as part of this local deployment.

## Secrets

`01-secrets.yaml` is a comment-only template. The deployment script reads `ASSISTANT_DB_PASSWORD` from the environment and accepts an optional `DEEPSEEK_API_KEY`; it creates the Secret without writing credential values to the repository. The application starts with DeepSeek disabled. To enable it, also set `DEEPSEEK_ENABLED` and `DEEPSEEK_MODEL` in `02-configmap.yaml`.

Set `ASSISTANT_DB_PASSWORD` in the deployment shell before using the one-click script. Do not place it in a checked-in file.

```powershell
kubectl -n cvp create secret generic cvp-ai-assistant-secrets --from-literal=ASSISTANT_DB_PASSWORD="$env:ASSISTANT_DB_PASSWORD" --from-literal=DEEPSEEK_API_KEY="$env:DEEPSEEK_API_KEY" --dry-run=client -o yaml | kubectl apply -f -
```

Do not commit a real API key. The current Phase 1 service uses local mock authentication by default (`ASSISTANT_MOCK_AUTHENTICATION_ENABLED=true`) and accepts a nonblank `X-Principal` request header. This is not production authentication; disable the flag only after configuring a production authentication adapter.

## Deploy

Apply files in this order:

```powershell
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/02-configmap.yaml
kubectl -n cvp create configmap assistant-knowledge --from-file=01-vehicle-maintenance-knowledge.md=../knowledge/01-vehicle-maintenance-knowledge.md --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/06-ai-assistant-db-init.yaml
kubectl wait --for=condition=complete job/ai-assistant-db-init -n cvp --timeout=120s
kubectl apply -f k8s/04-assistant.yaml
```

After reviewing the manifests, the complete directory can also be applied safely:

```powershell
kubectl apply -f k8s/
```

## Verify

```powershell
kubectl get pods,deployments,services -n cvp
kubectl rollout status deployment/cvp-ai-assistant -n cvp
kubectl logs deployment/cvp-ai-assistant -n cvp
```

In a second terminal, verify the standalone Inventory Service:

```powershell
Invoke-WebRequest -UseBasicParsing -Headers @{ Host = 'inventory.local' } http://127.0.0.1/v1/vehicles/TEST-VEHICLE/capability
```

Then port-forward the Assistant and check Actuator health:

```powershell
kubectl port-forward service/cvp-ai-assistant 8088:8080 -n cvp
Invoke-WebRequest -UseBasicParsing http://localhost:8088/actuator/health
```

The Assistant resolves Inventory through `cvp-inventory-service.cvp.svc.cluster.local:8080`, Maintenance Reminder Service through `maintenance-reminder.cvp.svc.cluster.local:8080`, and Telemetry through `cvp-telemetry-service.cvp.svc.cluster.local:8080`. Confirm those services exist before testing maintenance evidence.

Local API verification:

```powershell
$body = @{ vehicleId = 'TEST-VEHICLE'; userMessage = 'Why does my vehicle need maintenance?' } | ConvertTo-Json
Invoke-WebRequest -UseBasicParsing -Method Post -Uri http://localhost:8088/api/v1/assistant/chat -Headers @{ 'X-Principal' = 'local-user' } -ContentType 'application/json' -Body $body
```

## Cleanup

Remove only the Assistant resources:

```powershell
kubectl delete -f k8s/04-assistant.yaml
kubectl delete -f k8s/02-configmap.yaml
kubectl delete -f k8s/01-secrets.yaml
```

Keep the shared `Maintenance Reminder Service` namespace unless the Maintenance Reminder Service deployment is also being removed.
