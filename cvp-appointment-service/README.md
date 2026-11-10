# CVP Appointment Service

Owns the appointment lifecycle: `PENDING -> BOOKED -> COMPLETED`.

`POST /api/v1/appointments` records a pending booking request and submits it through the internal `PartnerIntegrationPort`. The REST adapter is the only component that contacts the unified OEM Partner Integration Layer; it contains no DMS-specific behavior. A partner acknowledgment is not a booking confirmation.

Later callbacks transition an appointment through `POST /api/v1/integration/appointments/{id}/confirmed` and `/completed`; they publish `appointment.booked.v1` and `appointment.completed.v1` respectively. Dealer master data and slot discovery are outside this service.

Run locally with `gradle bootRun`. PostgreSQL, Kafka, and the partner base URL are configured through `APPOINTMENT_*` environment variables.

OpenAPI is available at `/v3/api-docs` and Swagger UI at `/swagger-ui.html` (for the local deployment: `http://appointment.local/swagger-ui.html`).

For local Kubernetes deployment, run `./k8s/one-click-appointment-service-k8s-deploy.sh`. It follows the AI Assistant convention: it creates one tag in the format `yyyyMMdd-HHmmss`, builds `cvp-appointment-service:<tag>`, injects that immutable tag into a temporary manifest, deploys it, and verifies that both the Deployment and Pod use the same tag. The checked-in manifest deliberately retains the readable `:latest` placeholder. The next external dependency is the **Partner Integration Mock**.
