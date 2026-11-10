package org.cvp.assistant.tool;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolFactMapperTests {
    @Test
    void normalizesTypedMcpMaintenanceReminderEnvelopeWithoutRelaxingNestedVehicleValidation() throws Exception {
        var payload = JsonMapper.builder().build().readTree("""
                {"maintenanceStatus":{"vehicleId":"TEST-VEHICLE","odometerKm":14250},
                 "reminders":[{"maintenanceType":"OIL","status":"PENDING"}]}
                """);
        String facts = ToolFactMapper.facts("getMaintenanceStatus", payload, "TEST-VEHICLE");
        assertTrue(facts.contains("vehicleId=\"TEST-VEHICLE\""));
        assertTrue(facts.contains("maintenanceType=\"OIL\""));
    }
}
