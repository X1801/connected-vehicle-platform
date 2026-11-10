# Maintenance Reminder Service local Kubernetes deployment

These manifests reproduce the validated local Compose topology in namespace `cvp` using only Kubernetes Service DNS names. They are for a local development cluster, not a production deployment.

## Images

- `cvp-maintenance-reminder-service:latest` (the image produced by the existing Dockerfile)
- `cvp-notification-mock:1.0` (built from `mocks/notification-kafka/`)
- `postgres:16-alpine`
- `apache/kafka:3.9.0`
- `wiremock/wiremock:3.9.1`

The current active Docker engine did not retain the Maintenance Reminder Service application image when these manifests were created. Build the unchanged Dockerfile into the referenced local tag before deployment:

```powershell
docker build -t cvp-maintenance-reminder-service:latest .
docker build -t cvp-notification-mock:1.0 mocks/notification-kafka
```

Docker Desktop Kubernetes normally shares its Docker image store. If a different local cluster cannot see the image, load it using that cluster's documented local-image mechanism; do not publish it to a public registry.

## Apply and verify

```powershell
kubectl apply --dry-run=client --validate=false -f k8s/
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/
kubectl get pods,deployments,statefulsets,services,pvc -n cvp
kubectl logs deployment/maintenance-reminder -n cvp
kubectl port-forward service/maintenance-reminder 8088:8080 -n cvp
```

Then call `http://localhost:8088/actuator/health`.

Maintenance Reminder Service receives its existing configuration through `maintenance-reminder-runtime` and `maintenance-reminder-postgres`; it connects to `postgres:5432`, `kafka:9092`, and the HTTP mocks. The Kafka-capable Mock Notification Service consumes `cvp.notification-request.v1` and publishes `cvp.notification-status.v1`. PostgreSQL uses a 1 GiB PVC. Kafka is intentionally ephemeral and single-node for local development.
