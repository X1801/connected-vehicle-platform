package com.cvp.maintenance.adapter.inbound.kafka;

import com.cvp.maintenance.application.MaintenanceService;
import com.cvp.maintenance.application.InventorySynchronizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TelemetryMaintenanceStateListener {
    private final MaintenanceService service;
    private final ObjectMapper mapper;
    private final InventorySynchronizationService inventory;

    public TelemetryMaintenanceStateListener(MaintenanceService s, ObjectMapper m, InventorySynchronizationService inventory) {
        service = s;
        mapper = m;
        this.inventory = inventory;
    }

    @KafkaListener(topics = "${maintenance-reminder.integration.telemetry.maintenance-state-topic}")
    public void onMessage(String payload) throws Exception {
        service.ingest(mapper.readValue(payload, TelemetryMaintenanceStateEvent.class));
        try { inventory.synchronizeIfMissing(mapper.readValue(payload, TelemetryMaintenanceStateEvent.class).vehicleId()); } catch (RuntimeException ignored) { }
    }
}
