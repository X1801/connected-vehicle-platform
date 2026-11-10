package com.cvp.maintenance.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.*;

import java.time.*;
import java.util.*;

public interface MaintenancePlanRepository extends JpaRepository<MaintenancePlanEntity, UUID> {
    @Query("select p from MaintenancePlanEntity p where p.maintenanceType=:type and p.validFrom<=:today and (p.validTo is null or p.validTo>=:today)")
    List<MaintenancePlanEntity> findActive(String type, LocalDate today);
}
