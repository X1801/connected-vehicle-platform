package com.cvp.maintenance.adapter.outbound.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.time.Instant;
import java.sql.Types;

@Entity @Table(name = "vehicle_maintenance_status")
public class VehicleMaintenanceStatusEntity {
    @Id @Column(name = "vehicle_id", length = 64) public String vehicleId;
    @Column(name = "odometer_km", nullable = false) public long odometerKm;
    @Column(name = "source_timestamp", nullable = false) public Instant sourceTimestamp;
    @Column(name = "source_version") public Long sourceVersion;
    @JdbcTypeCode(Types.CHAR) @Column(name = "payload_checksum", nullable = false, length = 64, columnDefinition = "char(64)") public String payloadChecksum;
    @Column(name = "accepted_at", nullable = false) public Instant acceptedAt;
    @Version @Column(nullable = false) public long version;
    protected VehicleMaintenanceStatusEntity() { }
    public VehicleMaintenanceStatusEntity(String vehicleId) { this.vehicleId = vehicleId; }
}
