package com.cvp.maintenance.application;
import com.cvp.maintenance.adapter.outbound.persistence.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component public class InventoryReconciliationJob { private final VehicleMaintenanceStatusRepository statuses; private final VehicleReferenceRepository references; private final InventorySynchronizationService sync; InventoryReconciliationJob(VehicleMaintenanceStatusRepository s,VehicleReferenceRepository r,InventorySynchronizationService i){statuses=s;references=r;sync=i;} @Scheduled(fixedDelayString="${maintenance-reminder.inventory-reconciliation.fixed-delay-ms:300000}") public void reconcile(){statuses.findAll().stream().filter(s->!references.existsById(s.vehicleId)).limit(50).forEach(s->{try{sync.synchronize(s.vehicleId);}catch(RuntimeException ignored){}});} }
