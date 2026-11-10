package com.cvp.maintenance.adapter.outbound.persistence;
import org.springframework.data.jpa.repository.*; import java.util.*;
public interface MaintenanceIndicatorRepository extends JpaRepository<MaintenanceIndicatorEntity,UUID> { void deleteByStatusVehicleId(String vehicleId); List<MaintenanceIndicatorEntity> findByStatusVehicleId(String vehicleId); }
