package com.cvp.maintenance.application;

import com.cvp.maintenance.adapter.outbound.persistence.VehicleReferenceEntity;
import com.cvp.maintenance.adapter.outbound.persistence.VehicleReferenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.List;

@Service
public class InventorySynchronizationService {
    private final RestClient inventory; private final VehicleReferenceRepository references; private final MaintenanceService maintenance;
    public InventorySynchronizationService(RestClient.Builder builder, VehicleReferenceRepository references, MaintenanceService maintenance,
                                           @Value("${maintenance-reminder.integration.inventory.base-url}") String baseUrl) { this.inventory=builder.baseUrl(baseUrl).build(); this.references=references; this.maintenance=maintenance; }
    public void synchronize(String vehicleId) { Capability capability=inventory.get().uri("/v1/vehicles/{vehicleId}/capability", vehicleId).retrieve().body(Capability.class); if(capability==null||!vehicleId.equals(capability.vehicleId())||blank(capability.model())||blank(capability.market())||blank(capability.powertrain())||capability.modelYear()<1886) throw new IllegalArgumentException("Invalid Inventory vehicle capability"); persistAndReevaluate(capability); }
    public void synchronizeIfMissing(String vehicleId) { if (!references.existsById(vehicleId)) synchronize(vehicleId); }
    /** Inventory notifications are hints: the maintenance service reads authoritative state before persisting it. */
    public void synchronizeIfNewer(String vehicleId, long eventSourceVersion) {
        VehicleReferenceEntity current=references.findById(vehicleId).orElse(null);
        if (current!=null && current.sourceVersion!=null && current.sourceVersion>=eventSourceVersion) return;
        synchronize(vehicleId);
    }
    @Transactional void persistAndReevaluate(Capability c) { VehicleReferenceEntity ref=references.findById(c.vehicleId()).orElseGet(VehicleReferenceEntity::new); if(ref.sourceVersion!=null && ref.sourceVersion>c.sourceVersion())return; ref.vehicleId=c.vehicleId(); ref.externalVehicleReference=c.vehicleId(); ref.modelCode=c.model(); ref.modelYear=c.modelYear(); ref.marketCode=c.market(); ref.powertrainCode=c.powertrain(); ref.sourceVersion=c.sourceVersion(); ref.sourceUpdatedAt=c.sourceUpdatedAt(); ref.refreshedAt=Instant.now(); references.save(ref); try { maintenance.reevaluate(c.vehicleId()); } catch (NotFoundException ignored) { /* Telemetry may arrive after Inventory. */ } }
    private boolean blank(String value){return value==null||value.isBlank();}
    public record Capability(String vehicleId,String model,int modelYear,String market,String powertrain,List<String> supportedCapabilities,long sourceVersion,Instant sourceUpdatedAt){}
}
