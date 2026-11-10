#!/bin/bash

set -euo pipefail

NAMESPACE="cvp"
MAINTENANCE_REMINDER_IMAGE="cvp-maintenance-reminder-service:1.0"
NOTIFICATION_IMAGE="cvp-notification-mock:1.0"
MAINTENANCE_REMINDER_POSTGRES_USER="${CVP_MAINTENANCE_REMINDER_POSTGRES_USER:-}"
MAINTENANCE_REMINDER_POSTGRES_PASSWORD="${CVP_MAINTENANCE_REMINDER_POSTGRES_PASSWORD:-}"
: "${MAINTENANCE_REMINDER_POSTGRES_USER:?Set CVP_MAINTENANCE_REMINDER_POSTGRES_USER before deployment}"
: "${MAINTENANCE_REMINDER_POSTGRES_PASSWORD:?Set CVP_MAINTENANCE_REMINDER_POSTGRES_PASSWORD before deployment}"

echo "========================================"
echo "1. Cleaning old environment..."
echo "========================================"

kubectl delete -f k8s/06-maintenance-reminder.yaml --ignore-not-found=true --wait=true
kubectl delete -f k8s/05-mocks.yaml --ignore-not-found=true --wait=true
kubectl delete secret maintenance-reminder-postgres -n "$NAMESPACE" --ignore-not-found=true --wait=true
kubectl delete -f k8s/02-configmap.yaml --ignore-not-found=true --wait=true
kubectl delete -f k8s/07-ingress.yaml --ignore-not-found=true --wait=true

docker rmi "$NOTIFICATION_IMAGE" 2>/dev/null || true
docker rmi "$MAINTENANCE_REMINDER_IMAGE" 2>/dev/null || true


echo "========================================"
echo "2. Building Docker images..."
echo "========================================"

# Maintenance reminder service
docker build -t "$MAINTENANCE_REMINDER_IMAGE" .

# Notification mock
docker build -t "$NOTIFICATION_IMAGE" ./mocks/notification-kafka


echo "========================================"
echo "3. Validating Kubernetes manifests..."
echo "========================================"

kubectl apply --dry-run=client --validate=false -f k8s/00-namespace.yaml
kubectl apply --dry-run=client --validate=false -f k8s/02-configmap.yaml
kubectl apply --dry-run=client --validate=false -f k8s/03-postgres.yaml
kubectl apply --dry-run=client --validate=false -f k8s/04-kafka.yaml
kubectl apply --dry-run=client --validate=false -f k8s/05-mocks.yaml
kubectl apply --dry-run=client --validate=false -f k8s/06-maintenance-reminder.yaml


echo "========================================"
echo "4. Deploying Kubernetes resources..."
echo "========================================"

kubectl apply -f k8s/00-namespace.yaml

# Deploy infrastructure and the maintenance reminder service first.
kubectl apply -f k8s/00-namespace.yaml
kubectl create secret generic maintenance-reminder-postgres -n "$NAMESPACE" \
    --from-literal=POSTGRES_DB=maintenance-reminder \
    --from-literal=POSTGRES_USER="$MAINTENANCE_REMINDER_POSTGRES_USER" \
    --from-literal=POSTGRES_PASSWORD="$MAINTENANCE_REMINDER_POSTGRES_PASSWORD" \
    --from-literal=CVP_MAINTENANCE_REMINDER_DB_USERNAME="$MAINTENANCE_REMINDER_POSTGRES_USER" \
    --from-literal=CVP_MAINTENANCE_REMINDER_DB_PASSWORD="$MAINTENANCE_REMINDER_POSTGRES_PASSWORD" \
    --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/02-configmap.yaml
kubectl apply -f k8s/03-postgres.yaml
kubectl apply -f k8s/04-kafka.yaml
kubectl apply -f k8s/05-mocks.yaml
kubectl apply -f k8s/06-maintenance-reminder.yaml

echo "========================================"
echo "5. Waiting for maintenance reminder deployment..."
echo "========================================"

kubectl rollout status deployment/notification-mock -n "$NAMESPACE" --timeout=180s
kubectl rollout status deployment/appointment-provider-mock -n "$NAMESPACE" --timeout=180s
kubectl rollout status deployment/authorization-mock -n "$NAMESPACE" --timeout=180s
kubectl rollout status deployment/maintenance-reminder -n "$NAMESPACE" --timeout=180s

# 最后再：
kubectl apply -f k8s/07-ingress.yaml

echo "========================================"
echo "6. Checking Spring Boot startup..."
echo "========================================"

if ! kubectl logs deployment/maintenance-reminder -n "$NAMESPACE" | grep -q "Started MaintenanceReminderApplication in"; then
    echo "ERROR: Maintenance reminder startup failed."
    exit 1
fi

echo "Spring Boot started successfully."


#echo "========================================"
#echo "7. Starting port-forward..."
#echo "========================================"
#
#pkill -f "kubectl port-forward service/maintenance-reminder 8088:8080" 2>/dev/null || true
#
#kubectl port-forward service/maintenance-reminder 8088:8080 \
#    -n "$NAMESPACE" >/tmp/maintenance-reminder-port-forward.log 2>&1 &
#
#PORT_FORWARD_PID=$!
#
#sleep 3


echo "========================================"
echo "7. Checking /actuator/health..."
echo "========================================"

echo "Waiting for Ingress..."
for i in {1..10}; do
    if curl -s -H "Host: maintenance-reminder.local" \
        http://127.0.0.1/actuator/health | grep -q '"status":"UP"'; then
        break
    fi
    sleep 2
done

HEALTH=$(curl -s -H "Host: maintenance-reminder.local" \
    http://127.0.0.1/actuator/health)

echo "Health response: $HEALTH"

if echo "$HEALTH" | grep -q '"status":"UP"'; then
    echo ""
    echo "========================================"
    echo "SUCCESS: Maintenance reminder deployment completed."
    echo "Spring Boot: STARTED"
    echo "Health: UP"
    echo "URL: http://maintenance-reminder.local"
    echo "========================================"
else
    echo "ERROR: Maintenance reminder health check failed."
    kill "$PORT_FORWARD_PID" 2>/dev/null || true
    exit 1
fi
