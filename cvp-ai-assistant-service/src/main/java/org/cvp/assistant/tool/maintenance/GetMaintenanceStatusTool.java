package org.cvp.assistant.tool.maintenance;

import tools.jackson.databind.JsonNode;
import org.cvp.assistant.client.maintenance.UpstreamException;
import org.cvp.assistant.client.maintenance.MaintenanceReminderClientPort;
import org.cvp.assistant.domain.evidence.EvidenceSource;
import org.cvp.assistant.domain.evidence.ToolOutcome;
import org.cvp.assistant.tool.AssistantTool;
import org.cvp.assistant.tool.ToolExecutionContext;
import org.cvp.assistant.tool.ToolFactMapper;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "assistant.tools", name = "transport", havingValue = "direct", matchIfMissing = true)
public class GetMaintenanceStatusTool implements AssistantTool<String> {
    private final MaintenanceReminderClientPort maintenanceReminder;

    public GetMaintenanceStatusTool(MaintenanceReminderClientPort maintenanceReminder) {
        this.maintenanceReminder = maintenanceReminder;
    }

    @Override
    public String name() {
        return "getMaintenanceStatus";
    }

    @Override public String description() {
        return "Get authoritative maintenance status and scheduled reminders for the authorized vehicle.";
    }

    @Override
    public ToolOutcome execute(ToolExecutionContext context, String vehicleId) {
        context.requireVehicle(vehicleId);
        try {
            JsonNode status = maintenanceReminder.maintenanceStatus(vehicleId);
            Instant timestamp = status.hasNonNull("sourceTimestamp") ? Instant.parse(status.get("sourceTimestamp").asText()) : null;
            EvidenceSource source = new EvidenceSource("maintenance-reminder-status-" + vehicleId, "BUSINESS_API", "MAINTENANCE_REMINDER", vehicleId, Instant.now(), timestamp, "MAINTENANCE_REMINDER");
            ToolFactMapper.requireVehicle(status, vehicleId);
            try {
                JsonNode reminders = maintenanceReminder.reminders(vehicleId);
                String summary = "Maintenance status retrieved" + (reminders.isArray() && reminders.isEmpty() ? "; no reminders were returned" : "; reminder data retrieved");
                return ToolOutcome.success(name(), summary, facts(status, reminders), List.of(source));
            } catch (UpstreamException reminderFailure) {
                return ToolOutcome.success(name(), "Maintenance status retrieved; reminder data is unavailable",
                        facts(status, null) + "\nreminderDataStatus=" + (reminderFailure.timeout() ? "TIMEOUT" : reminderFailure.status() == 404 ? "NOT_FOUND" : "UNAVAILABLE"), List.of(source));
            } catch (RuntimeException reminderFailure) {
                return ToolOutcome.success(name(), "Maintenance status retrieved; reminder data is unavailable",
                        facts(status, null) + "\nreminderDataStatus=UNAVAILABLE", List.of(source));
            }
        } catch (UpstreamException exception) {
            return ToolOutcome.failure(name(), code(exception.status()), exception.getMessage(), retryable(exception.status()));
        } catch (RuntimeException exception) {
            return ToolOutcome.failure(name(), "INVALID_UPSTREAM_RESPONSE", "The maintenance service returned an invalid response", false);
        }
    }

    private String code(int status) {
        return status == 504 ? "TIMEOUT" : status == 404 ? "NOT_FOUND" : status == 403 ? "FORBIDDEN" : status == 429 ? "RATE_LIMITED" : "UPSTREAM_UNAVAILABLE";
    }

    private boolean retryable(int status) {
        return status == 429 || status >= 500;
    }

    /** Deliberately allow-listed maintenance facts; no raw upstream payload is exposed to the LLM. */
    private String facts(JsonNode status, JsonNode reminders) {
        List<String> lines = new ArrayList<>();
        lines.add("maintenanceStatus: vehicleId=" + text(status, "vehicleId")
                + ", odometerKm=" + text(status, "odometerKm")
                + ", sourceTimestamp=" + text(status, "sourceTimestamp")
                + ", sourceVersion=" + text(status, "sourceVersion"));
        if (reminders != null && reminders.isArray() && reminders.isEmpty()) lines.add("reminders: none");
        if (reminders != null && reminders.isArray()) {
            int count = 0;
            for (JsonNode reminder : reminders) {
                if (count++ == 10) break;
                lines.add("reminder: type=" + text(reminder, "maintenanceType")
                        + ", status=" + text(reminder, "status")
                        + ", severity=" + text(reminder, "severity")
                        + ", dueOdometerKm=" + text(reminder, "dueOdometerKm")
                        + ", dueDate=" + text(reminder, "dueDate")
                        + ", notificationStatus=" + text(reminder, "notificationStatus"));
            }
        }
        return String.join("\n", lines);
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : "unknown";
    }
}
