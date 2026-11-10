# Connected Vehicle Platform

Connected Vehicle Platform (CVP) is a Java microservices project for vehicle maintenance workflows. It combines vehicle capability data, maintenance telemetry, reminders, appointment booking, and an AI assistant that can query domain services through Model Context Protocol (MCP). The current Kubernetes setup is a local Docker Desktop environment in the `cvp` namespace.

The platform is useful for exploring event-driven maintenance workflows, appointment partner callbacks, and tool-assisted vehicle questions. Vehicle telemetry entry points, partner booking, and notification delivery currently use development mocks or synthetic data; this is not a production vehicle or dealer integration.

## Architecture

| Service | Responsibility | Main dependencies |
| --- | --- | --- |
| AI Assistant | REST chat, vehicle access checks, retrieval from project knowledge, DeepSeek calls, and MCP tool orchestration | PostgreSQL, DeepSeek API, MCP Server |
| Inventory | Vehicle master data and capabilities; publishes master-data changes | PostgreSQL, Kafka |
| Telemetry | Vehicle maintenance state and synthetic event ingestion; publishes state changes | PostgreSQL, Kafka |
| Maintenance Reminder | Evaluates maintenance conditions, tracks reminder lifecycle, and reacts to appointment and notification events | PostgreSQL, Kafka, Inventory, Telemetry |
| Appointment | Creates bookings, receives partner callbacks, and publishes booked/completed events | PostgreSQL, Kafka, Partner Integration Mock |
| Partner Integration Mock | Accepts booking submissions and exposes development endpoints to confirm or complete them | Appointment callback API |
| MCP Server | Exposes maintenance, telemetry, and inventory queries as three MCP tools | Maintenance Reminder, Telemetry, Inventory |

PostgreSQL is shared infrastructure with separate application databases. Kafka carries domain events. The local overlay also includes a Kafka-backed Notification Mock, which consumes notification requests and publishes delivery status. NGINX Ingress routes each application under its own local host. Service-to-service REST calls use Kubernetes Service names from [`cvp-app-config`](k8s/base/infrastructure/app-config.yaml).

### Core flows

1. **Vehicle state to reminder:** Inventory changes publish `inventory.vehicle-master-changed.v1`; synthetic telemetry updates publish `telemetry.vehicle-maintenance-state.v1`. Maintenance Reminder consumes these events, queries current vehicle data, and evaluates reminder state.
2. **Notification:** Maintenance Reminder publishes `cvp.notification-request.v1`; the Notification Mock consumes it and publishes `cvp.notification-status.v1`. There is no production notification provider in the active deployment.
3. **Booking:** Appointment creates a booking and sends it to Partner Integration Mock. Mock confirm/complete actions call Appointment's callback API, which publishes `appointment.booked.v1` and `appointment.completed.v1`. Maintenance Reminder consumes those appointment events.
4. **Assistant:** AI Assistant accepts a chat request, uses local knowledge for retrieval, and can invoke MCP Server tools for maintenance status, telemetry state, and inventory capability. The local overlay enables DeepSeek; requests require a valid API key and network access. MCP returns structured domain results rather than replacing the domain services.

## Technology stack

- Java 21; Spring Boot, Spring Web, Actuator, validation, Spring Data JPA, and Flyway where used by each service.
- Spring Kafka for domain events; PostgreSQL 16 for persistent application data; a single Apache Kafka 3.9.0 KRaft broker in the local Kubernetes overlay.
- Springdoc OpenAPI and Swagger UI for the six REST services; Spring AI MCP Server with Streamable HTTP for MCP Server.
- DeepSeek HTTP API for the configured LLM integration, plus project-owned knowledge in `knowledge/` for assistant retrieval.
- Maven wrappers for AI Assistant and Maintenance Reminder; Gradle wrappers for the other five services.
- Docker images imported into Docker Desktop Kubernetes node containerd, Kustomize for resource composition, and the existing NGINX Ingress Controller.

## Project structure

```text
connected-vehicle-platform/
├── cvp-ai-assistant-service/         # Chat, retrieval, LLM and tool orchestration
├── cvp-appointment-service/          # Booking and partner callbacks
├── cvp-inventory-service/            # Vehicle master data and capability API
├── cvp-maintenance-reminder-service/ # Reminder evaluation and lifecycle
├── cvp-mcp-server/                   # Streamable HTTP MCP tools
├── cvp-partner-integration-mock/     # Development booking partner
├── cvp-telemetry-service/            # Maintenance state and synthetic events
├── docs/                             # Architecture, context diagrams, implementation notes
├── knowledge/                        # Assistant retrieval content
├── k8s/base/                         # Seven service groups and shared infrastructure
├── k8s/overlays/local/               # Docker Desktop composition for namespace cvp
├── scripts/deploy/                   # Deployment, image import, and verification scripts
├── prompts/                           # Task-specific project instructions
└── AGENTS.md                          # Repository guidance
```

The centralized `k8s/` manifests and `scripts/deploy/` entry points are the deployment path. Some service directories retain older local manifests or scripts as references; use the centralized scripts for this environment.

## Getting started

### Prerequisites

Use a Bash-capable shell (Git Bash on Windows), Docker Desktop with its Kubernetes cluster enabled, `kubectl`, Docker CLI, `curl`, and `tar`. Image builds also need dependency downloads and the included Maven/Gradle wrappers; Java 21 is the project toolchain. The deployment scripts expect Kubernetes context `docker-desktop`, namespace `cvp`, an existing NGINX `IngressClass`, and an existing `cvp-database-credentials` Secret. The image import process uses a temporary privileged helper Pod to import images into the node's containerd `k8s.io` namespace.

Check the target before running any deployment:

```bash
kubectl config current-context
kubectl get namespace cvp
kubectl -n cvp get secret cvp-database-credentials
kubectl get ingressclass nginx
kubectl -n cvp get deployments,pods,pvc
```

The required Secret has three keys: `CVP_DB_PASSWORD`, `AI_ASSISTANT_DB_PASSWORD`, and `DEEPSEEK_API_KEY`. Provision or restore it through your secret-management process; keep values out of the repository and command logs. The deployment scripts verify that the Secret exists but do not create it. `DEEPSEEK_API_KEY` is read by AI Assistant, while [`cvp-app-config`](k8s/base/infrastructure/app-config.yaml) sets `DEEPSEEK_ENABLED`, `DEEPSEEK_BASE_URL`, and `DEEPSEEK_MODEL` for the local deployment. A usable key is required for live DeepSeek requests.

The PostgreSQL StatefulSet uses PVC `cvp-postgres-data`. On a **new, empty** data volume, [`cvp-postgres-init`](k8s/base/infrastructure/postgres/configmap.yaml) creates the `appointment`, `inventory`, `telemetry`, and `ai_assistant` databases; the initial PostgreSQL database is `maintenance-reminder`. Application schemas are managed by each service's migrations. The initialization script is not a migration or reset procedure for an existing volume. Keep and back up the PVC when preserving data.

The current deployment entry points are designed to adopt an existing installation: they read the seven live Deployment image references and require the namespace and Secret in advance. A complete, automated bootstrap from an empty cluster is not provided here. `--allow-new-infrastructure` permits a missing PostgreSQL PVC only when a fresh installation is intentional; it does not supply missing credentials or live service images.

### Build and deployment

Run these from the repository root. The default no-build path reapplies the current per-service image references; it does not generate tags or rebuild images. A full build generates one UTC `yyyyMMdd-HHmmss` tag shared by all seven services. An independent build tags and deploys only its selected service. `--tag` selects an existing imported tag without a build, or chooses an explicit new tag together with `--build`.

```bash
# Validate the full overlay against the current cluster without applying it.
bash scripts/deploy/deploy-all.sh --dry-run

# Reapply current images and verify the complete deployment.
bash scripts/deploy/deploy-all.sh

# Build, import, and deploy one new seven-service release.
bash scripts/deploy/deploy-all.sh --build

# Upgrade only Inventory with a newly built image.
bash scripts/deploy/deploy-inventory.sh --build

# Reapply only Inventory's current image, or select an already imported tag.
bash scripts/deploy/deploy-inventory.sh
bash scripts/deploy/deploy-inventory.sh --tag YYYYMMDD-HHmmss

# Select an already imported tag for all seven services, if available for each.
bash scripts/deploy/deploy-all.sh --tag YYYYMMDD-HHmmss
```

The other independent entry points are `deploy-ai-assistant.sh`, `deploy-appointment.sh`, `deploy-maintenance-reminder.sh`, `deploy-partner-mock.sh`, `deploy-mcp-server.sh`, and `deploy-telemetry.sh` in `scripts/deploy/`. They accept the same `--build`, `--dry-run`, and `--tag` options. Image builds use temporary source copies and import the resulting images into the Kubernetes node; no external registry push is part of this workflow. The scripts render the selected live image tags through a temporary Kustomize layer, so the static base image tags are not the version source for a no-build deployment.

## API documentation and MCP

The local Ingress configuration defines these HTTP routes. Browser access requires the `.local` hosts to resolve to your existing Ingress controller; [`verify-all.sh`](scripts/deploy/verify-all.sh) can test with a temporary `curl --resolve` mapping without changing system DNS. These are configured URLs, not a claim of live availability in every checkout.

| REST service | Swagger UI | OpenAPI JSON |
| --- | --- | --- |
| AI Assistant | <http://assistant.local/swagger-ui/index.html> | <http://assistant.local/v3/api-docs> |
| Appointment | <http://appointment.local/swagger-ui/index.html> | <http://appointment.local/v3/api-docs> |
| Maintenance Reminder | <http://maintenance-reminder.local/swagger-ui/index.html> | <http://maintenance-reminder.local/v3/api-docs> |
| Partner Integration Mock | <http://partner-mock.local/swagger-ui/index.html> | <http://partner-mock.local/v3/api-docs> |
| Inventory | <http://inventory.local/swagger-ui/index.html> | <http://inventory.local/v3/api-docs> |
| Telemetry | <http://telemetry.local/swagger-ui/index.html> | <http://telemetry.local/v3/api-docs> |

Examples of implemented REST routes include Inventory `GET /v1/vehicles/{id}/capability`, Telemetry `GET /v1/vehicles/{id}/maintenance-state`, Maintenance Reminder `GET /api/v1/vehicles/{vehicleId}/maintenance-status`, Appointment `POST /api/v1/appointments`, Partner Integration Mock `GET /mock/v1/bookings`, and AI Assistant `POST /api/v1/assistant/chat`. Use each service's OpenAPI document for current request and response schemas.

MCP Server uses **Streamable HTTP** at <http://mcp.local/mcp>; it does not expose Swagger. It registers `getMaintenanceStatus`, `getVehicleMaintenanceState`, and `getVehicleCapability`, each taking an authorized `vehicleId`. Test it with an MCP client using JSON-RPC `initialize` followed by `tools/list`, or run `verify-all.sh`, which performs this exchange through Ingress. A plain browser GET is not a reliable MCP health check.

## Verification and tests

```bash
kubectl kustomize k8s/overlays/local
bash scripts/deploy/verify-all.sh
kubectl -n cvp get deployments,pods,services,ingress,pvc
```

`verify-all.sh` checks PostgreSQL readiness and its PVC, Kafka topic-list connectivity, all seven application rollouts and Services, each configured Ingress route, six Swagger UI/OpenAPI pairs, and an MCP initialization and tool-list exchange. `scripts/deploy/test-image-tags.sh` contains focused tests for deployment tag behavior. Service modules also contain Java tests run through their Maven or Gradle wrappers. Deployment verification is a runtime smoke check, not a full end-to-end business regression suite. This README was written from source and configuration; it does not assert a fresh live-cluster test result.

## Configuration and integration boundaries

- **PostgreSQL:** One StatefulSet and persistent volume, with application-specific databases. Database credentials come from the Kubernetes Secret, not from a committed manifest.
- **Kafka:** A single local KRaft broker at `kafka:9092`. Its current manifest has no PVC, so broker data is not durable across replacement of its Pod.
- **DeepSeek:** The local overlay enables `deepseek-chat` at `https://api.deepseek.com`. AI Assistant reads the API key from the Secret. Calls may require network access and incur provider charges.
- **Partner and notification:** Partner Integration Mock uses in-memory booking state and developer-controlled callbacks; Notification Mock simulates delivery via Kafka. These are not external production services.
- **Security:** The local AI configuration enables mock authentication and limits the default allowed vehicle list to `TEST-VEHICLE`. Treat the local routes as development endpoints, not as a production security setup.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Deployment preflight fails | Confirm `docker-desktop` context, `cvp` namespace, `cvp-database-credentials` Secret, and `nginx` IngressClass. |
| Build stalls or fails on dependencies | Check Maven Central access and the local Maven/Gradle caches. Retry only after the network or repository issue is resolved; builds use the included wrappers. |
| `ImagePullBackOff` or selected tag unavailable | Confirm the intended image was imported into the Docker Desktop node's containerd `k8s.io` namespace. Docker's local image list alone is insufficient. |
| Pod is not Ready | Inspect `kubectl -n cvp describe pod <pod>` and `kubectl -n cvp logs <pod>`, then check its ConfigMap and Secret references. |
| Database connection or migration fails | Check `cvp-postgres`, the bound `cvp-postgres-data` PVC, Secret keys, and service migrations; do not rerun initialization against existing data. |
| Kafka-dependent workflow stalls | Check `cvp-kafka` readiness, topic connectivity, and service logs. The local broker has no persistent volume. |
| Ingress URL does not resolve | Check local host resolution separately from routing; `verify-all.sh` tests the controller with `curl --resolve`. Confirm the existing NGINX controller address and Ingress host. |
| Swagger or MCP check fails | Check the specific service rollout and Ingress backend. Swagger applies only to the six REST services; MCP requires protocol-aware JSON-RPC requests. |
| AI response fails | Check `DEEPSEEK_API_KEY`, model/base URL, egress, MCP Server readiness, and the local vehicle-access setting without printing secret values. |

For current resource definitions and deployment behavior, consult [`k8s/overlays/local/kustomization.yaml`](k8s/overlays/local/kustomization.yaml), [`k8s/base/infrastructure/`](k8s/base/infrastructure/), and [`scripts/deploy/`](scripts/deploy/).
