# Connected Vehicle Platform MCP Server

Phase 2 adds an internal MCP transport without replacing the Assistant's direct tools. The Assistant remains responsible for authorization, trusted vehicle binding, tool allowlisting, dynamic LLM selection, conversation state, and RAG. This service only exposes the existing Maintenance Reminder Service, Telemetry, and Inventory capabilities as official Spring AI MCP tools.

The server uses Spring AI's Streamable HTTP MCP transport at `/mcp`. It suits Kubernetes service-to-service calls, supports MCP discovery and sessions, and avoids exposing an external ingress. Maintenance Reminder Service remains authoritative; the server does not recalculate maintenance decisions.

## Run and verify

Use Java 21. On Windows run `./gradlew.bat clean test` then `./gradlew.bat bootJar`; on Unix run `./gradlew clean test` then `./gradlew bootJar`. Build with `docker build -t cvp-mcp-server:local .`.

Kubernetes uses the internal `cvp-mcp-server.cvp.svc.cluster.local:8090/mcp` endpoint. Run `./k8s/one-click-mcp-k8s-deploy.sh` from any directory. It first deploys the baseline Assistant, builds/tests a timestamped MCP image, substitutes that exact tag into a temporary manifest, rolls out MCP, and explicitly switches the Assistant to `ASSISTANT_TOOLS_TRANSPORT=mcp`.

Direct mode remains the default through `ASSISTANT_TOOLS_TRANSPORT=direct`. Restore it with `kubectl set env deployment/cvp-ai-assistant -n cvp ASSISTANT_TOOLS_TRANSPORT=direct` followed by rollout verification. `/actuator/health` and `/actuator/prometheus` are available internally. For troubleshooting, inspect MCP and Assistant logs and confirm Maintenance Reminder Service, `cvp-telemetry-service`, and `cvp-inventory-service` services are healthy.
