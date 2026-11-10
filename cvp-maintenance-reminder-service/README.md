# Vehicle Maintenance Reminder Service (Maintenance Reminder Service)

Maintenance Reminder Service is a Spring Boot microservice for evaluating vehicle maintenance status, managing maintenance reminders, and coordinating notification and appointment integrations in an independent connected-vehicle demonstration platform.

## Architecture

The architecture source of truth is [docs/maintenance-reminder-service-architecture-design.md](workflow/docs/01-maintenance-reminder-service-architecture-design.md). It defines the service boundary, external integrations, persistence model, reliability approach, and phased delivery plan.

## Current implementation phase

The repository currently contains the Phase 1 project foundation only:

- Spring Boot and Maven build configuration
- PostgreSQL, Kafka, Flyway, security, observability, and test dependencies
- External-service configuration placeholders
- PostgreSQL and Kafka local infrastructure via Docker Compose
- Empty hexagonal-architecture package structure

Business logic, database entities, REST controllers, Kafka producers/consumers, and Flyway business migrations have intentionally not been implemented yet.

## Run locally

Prerequisites: Java 21 and Docker Desktop (or a compatible Docker runtime).

Start PostgreSQL and Kafka:

```powershell
docker compose up -d
```

Run the application on Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS or Linux:

```bash
./mvnw spring-boot:run
```

Set `CVP_MAINTENANCE_REMINDER_POSTGRES_USER`, `CVP_MAINTENANCE_REMINDER_POSTGRES_PASSWORD`, `TELEMETRY_POSTGRES_USER`, and `TELEMETRY_POSTGRES_PASSWORD` in your local environment before starting Docker Compose. Database credentials have no committed defaults. Override service endpoints and Kafka topic names through the documented environment variables. The Maven wrapper downloads its configured Maven distribution on first use.

The Kubernetes one-click deployment also requires `CVP_MAINTENANCE_REMINDER_POSTGRES_USER` and `CVP_MAINTENANCE_REMINDER_POSTGRES_PASSWORD`; it creates the `maintenance-reminder-postgres` Secret from those environment values. The telemetry deployment uses that Secret and requires the telemetry database to be provisioned in PostgreSQL.

Stop local infrastructure:

```powershell
docker compose down
```
