package org.cvp.assistant.client.maintenance;

import tools.jackson.databind.JsonNode;

public interface MaintenanceReminderClientPort {
    JsonNode maintenanceStatus(String vehicleId);
    JsonNode reminders(String vehicleId);
    JsonNode reminderDetail(String reminderId);
}
