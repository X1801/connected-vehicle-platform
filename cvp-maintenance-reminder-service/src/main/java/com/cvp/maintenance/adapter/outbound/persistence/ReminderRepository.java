package com.cvp.maintenance.adapter.outbound.persistence;

import com.cvp.maintenance.domain.ReminderSeverity;
import com.cvp.maintenance.domain.ReminderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.*;

public interface ReminderRepository extends JpaRepository<ReminderEntity, UUID> {
    @Query("select r from ReminderEntity r where r.vehicleId = :vehicleId " +
            "and (:status is null or r.status = :status) " +
            "and (:severity is null or r.severity = :severity)")
    Page<ReminderEntity> findForVehicle(String vehicleId, ReminderStatus status, ReminderSeverity severity, Pageable pageable);

    Optional<ReminderEntity> findByVehicleIdAndMaintenanceTypeAndMaintenanceCycleReference(String vehicleId, String maintenanceType, String cycle);

    Optional<ReminderEntity> findByVehicleIdAndMaintenanceTypeAndAuthoritativeCycleId(String vehicleId, String maintenanceType, UUID authoritativeCycleId);

    @Query("select r from ReminderEntity r where r.vehicleId=:vehicleId and r.maintenanceType=:maintenanceType and r.status in (com.cvp.maintenance.domain.ReminderStatus.PENDING,com.cvp.maintenance.domain.ReminderStatus.SCHEDULED,com.cvp.maintenance.domain.ReminderStatus.ACKNOWLEDGED)")
    List<ReminderEntity> findActiveForAppointment(String vehicleId, String maintenanceType);

    List<ReminderEntity> findByVehicleIdAndMaintenanceType(String vehicleId, String maintenanceType);
}
