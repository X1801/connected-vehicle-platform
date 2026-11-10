package com.cvp.maintenance.adapter.outbound.persistence;
import jakarta.persistence.*; import java.time.*;
@Entity @Table(name="vehicle_reference") public class VehicleReferenceEntity {
 @Id @Column(name="vehicle_id") public String vehicleId; public String externalVehicleReference; public String modelCode; public Integer modelYear; public String marketCode; public String powertrainCode; public Long sourceVersion; public Instant sourceUpdatedAt; public Instant refreshedAt;
 public VehicleReferenceEntity() { }
}
