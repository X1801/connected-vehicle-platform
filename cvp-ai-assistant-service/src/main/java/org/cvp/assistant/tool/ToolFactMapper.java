package org.cvp.assistant.tool;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Allow-listed business facts shared by direct and MCP adapters. */
public final class ToolFactMapper {
    private ToolFactMapper() { }

    public static String facts(String tool, JsonNode payload, String vehicleId) {
        return switch (tool) {
            case "getMaintenanceStatus" -> maintenanceEnvelope(payload, vehicleId);
            case "getVehicleMaintenanceState" -> { requireVehicle(payload, vehicleId); yield telemetry(payload); }
            case "getVehicleCapability" -> { requireVehicle(payload, vehicleId); yield inventory(payload); }
            case "getReminderDetail" -> { requireVehicle(payload, vehicleId); yield reminder(payload); }
            default -> throw new IllegalArgumentException("Unsupported evidence tool");
        };
    }

    public static void requireVehicle(JsonNode payload, String vehicleId) {
        if (!payload.hasNonNull("vehicleId") || !vehicleId.equals(payload.get("vehicleId").asText())) {
            throw new IllegalArgumentException("Tool returned another vehicle");
        }
    }

    private static String maintenance(JsonNode payload) {
        return maintenance(payload, payload.path("reminders"));
    }

    /** MCP combines the authoritative maintenance reminder service status and its reminder collection in one typed result. */
    private static String maintenanceEnvelope(JsonNode payload, String vehicleId) {
        if (payload.path("maintenanceStatus").isObject()) {
            JsonNode status = payload.path("maintenanceStatus");
            requireVehicle(status, vehicleId);
            return maintenance(status, payload.path("reminders"));
        }
        requireVehicle(payload, vehicleId);
        return maintenance(payload);
    }

    private static String maintenance(JsonNode payload, JsonNode reminders) {
        List<String> lines = new ArrayList<>();
        lines.add("maintenanceStatus: " + fields(payload, "vehicleId", "odometerKm", "sourceTimestamp", "sourceVersion"));
        if (reminders.isArray()) for (JsonNode reminder : reminders) {
            lines.add("reminder: " + fields(reminder, "id", "maintenanceType", "status", "severity", "dueOdometerKm", "dueDate", "notificationStatus", "authoritativeCycleId", "policyVersion"));
        }
        return String.join("\n", lines);
    }

    private static String telemetry(JsonNode payload) {
        List<String> lines = new ArrayList<>();
        lines.add("telemetryState: " + fields(payload, "vehicleId", "odometerKm", "sourceVersion", "sourceTimestamp", "eventId", "occurredAt"));
        JsonNode indicators = payload.path("serviceIndicators");
        if (indicators.isArray()) for (JsonNode indicator : indicators) {
            lines.add("indicator: " + fields(indicator, "code", "dueOdometerKm", "dueDate", "remainingDistanceKm", "remainingDays", "severity", "sourceTimestamp", "maintenanceCycleId"));
        }
        return String.join("\n", lines);
    }

    private static String inventory(JsonNode payload) {
        return "inventoryCapability: " + fields(payload, "vehicleId", "model", "modelYear", "market", "powertrain", "supportedCapabilities", "sourceVersion", "sourceUpdatedAt");
    }

    private static String reminder(JsonNode payload) {
        return "reminderDetail: " + fields(payload, "id", "vehicleId", "maintenanceType", "status", "severity", "dueOdometerKm", "dueDate", "notificationStatus", "authoritativeCycleId", "policyVersion");
    }

    private static String fields(JsonNode node, String... names) {
        List<String> values = new ArrayList<>();
        for (String name : names) if (node.hasNonNull(name)) values.add(name + "=" + node.get(name));
        return values.isEmpty() ? "no-allowlisted-facts" : String.join(", ", values);
    }
}
