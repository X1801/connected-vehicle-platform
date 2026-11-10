package com.cvp.maintenance.adapter.inbound.kafka;

import java.time.*;
import java.util.*;

public record TelemetryMaintenanceStateEvent(UUID eventId, String eventType, String vehicleId, Instant occurredAt,
                                       Long sourceVersion, Instant sourceTimestamp, long odometerKm,
                                       List<Indicator> serviceIndicators) {
    public record Indicator(String code, Long dueOdometerKm, LocalDate dueDate, Long remainingDistanceKm,
                            Integer remainingDays, String severity, Instant sourceTimestamp, UUID maintenanceCycleId) {
        public Indicator(String code, Long dueOdometerKm, LocalDate dueDate, Long remainingDistanceKm,
                         Integer remainingDays, String severity, Instant sourceTimestamp) {
            this(code, dueOdometerKm, dueDate, remainingDistanceKm, remainingDays, severity, sourceTimestamp, null);
        }
    }
}
