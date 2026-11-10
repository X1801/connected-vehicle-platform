package com.cvp.maintenance.adapter.outbound.persistence;

import com.cvp.maintenance.domain.ReminderSeverity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "maintenance_indicator", uniqueConstraints = @UniqueConstraint(columnNames = {"vehicle_id", "indicator_code"}))
public class MaintenanceIndicatorEntity {
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vehicle_id", nullable = false) public VehicleMaintenanceStatusEntity status;
    @Column(name = "indicator_code", nullable = false) public String indicatorCode;
    public Long dueOdometerKm; public LocalDate dueDate; public Long remainingDistanceKm; public Integer remainingDays; public UUID maintenanceCycleId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public ReminderSeverity severity;
    @Column(nullable = false) public Instant sourceTimestamp;
    public MaintenanceIndicatorEntity() { }
}
