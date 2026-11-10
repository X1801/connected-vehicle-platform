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
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "assistant.tools", name = "transport", havingValue = "direct", matchIfMissing = true)
public class GetReminderDetailTool implements AssistantTool<String> {
    private final MaintenanceReminderClientPort maintenanceReminder;

    public GetReminderDetailTool(MaintenanceReminderClientPort maintenanceReminder) {
        this.maintenanceReminder = maintenanceReminder;
    }

    @Override
    public String name() {
        return "getReminderDetail";
    }

    @Override
    public String description() {
        return "Get an authoritative reminder detail for the authorized vehicle.";
    }

    @Override
    public ToolOutcome execute(ToolExecutionContext context, String reminderId) {
        try {
            JsonNode detail = maintenanceReminder.reminderDetail(reminderId);
            if (!detail.hasNonNull("vehicleId") || !context.vehicleId().equals(detail.get("vehicleId").asText())) {
                return ToolOutcome.failure(name(), "FORBIDDEN", "Reminder does not belong to the authorized vehicle", false);
            }
            return ToolOutcome.success(name(), "Reminder detail retrieved", ToolFactMapper.facts(name(), detail, context.vehicleId()), List.of(new EvidenceSource(
                    "maintenance-reminder-" + reminderId, "BUSINESS_API", "MAINTENANCE_REMINDER", reminderId, Instant.now(), null, "MAINTENANCE_REMINDER")));
        } catch (UpstreamException exception) {
            return ToolOutcome.failure(name(), exception.timeout() ? "TIMEOUT" : exception.status() == 404 ? "NOT_FOUND" : "UPSTREAM_UNAVAILABLE", exception.getMessage(), exception.timeout() || exception.status() >= 500);
        }
    }
}
