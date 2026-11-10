package com.cvp.maintenance.adapter.outbound.persistence;

import com.cvp.maintenance.domain.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.time.*;
import java.sql.Types;
import java.util.UUID;

@Entity @Table(name = "reminder", uniqueConstraints = {@UniqueConstraint(columnNames = {"vehicle_id","maintenance_type","maintenance_cycle_reference"}), @UniqueConstraint(columnNames = "deduplication_key")})
public class ReminderEntity {
    @Id public UUID id;
    @Column(nullable = false) public String vehicleId; @Column(nullable = false) public String maintenanceType;
    @JdbcTypeCode(Types.CHAR) @Column(nullable = false, length = 64, columnDefinition = "char(64)") public String maintenanceCycleReference;
    @Column(name = "authoritative_cycle_id") public UUID authoritativeCycleId;
    public Long dueOdometerKm; public LocalDate dueDate; public Long remainingDistanceKm; public Integer remainingDays;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public ReminderSeverity severity;
    @Column(nullable = false) public String policyVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public ReminderStatus status;
    public Instant nextActionAt; @Column(nullable = false) public String deduplicationKey;
    @Column(nullable = false) public Instant createdAt; @Column(nullable = false) public Instant updatedAt;
    @Version @Column(nullable = false) public long version;
    public ReminderEntity() { }
}
