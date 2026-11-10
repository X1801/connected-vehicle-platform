package com.cvp.maintenance.adapter.outbound.persistence;

import com.cvp.maintenance.domain.ReminderSeverity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name = "maintenance_plan")
public class MaintenancePlanEntity {
    @Id public UUID id;
    @Column(nullable = false) public String maintenanceType;
    @Column(nullable = false) public String applicabilityScopeIdentity;
    public String modelCode; public Integer modelYearFrom; public Integer modelYearTo; public String marketCode; public String powertrainCode;
    public Long distanceThresholdKm; public Integer timeThresholdDays;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public ReminderSeverity severityPolicy;
    @Column(nullable = false) public String policyVersion;
    @Column(nullable = false) public LocalDate validFrom; public LocalDate validTo;
    @Column(nullable = false) public Instant createdAt;
    protected MaintenancePlanEntity() { }
}
