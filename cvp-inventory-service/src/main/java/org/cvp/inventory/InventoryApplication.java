package org.cvp.inventory;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@org.springframework.boot.autoconfigure.SpringBootApplication
@EnableScheduling public class InventoryApplication { public static void main(String[] args){org.springframework.boot.SpringApplication.run(InventoryApplication.class,args);} }

@Entity @Table(name="inventory_vehicle") class Vehicle {
 @Id @Column(name="vehicle_id") String vehicleId; @Column(name="model_code") String modelCode; @Column(name="model_year") int modelYear; @Column(name="market_code") String marketCode; @Column(name="powertrain_code") String powertrainCode; @JdbcTypeCode(SqlTypes.JSON) @Column(name="supported_capabilities",columnDefinition="jsonb") String supportedCapabilities; @Column(name="source_version") long sourceVersion; @Column(name="updated_at") Instant updatedAt;
 protected Vehicle(){} Vehicle(String id,Request r,long v){vehicleId=id;apply(r,v);} void apply(Request r,long v){modelCode=r.model();modelYear=r.modelYear();marketCode=r.market();powertrainCode=r.powertrain();supportedCapabilities="[\""+String.join("\",\"",r.supportedCapabilities())+"\"]";sourceVersion=v;updatedAt=Instant.now();}
}
interface Vehicles extends JpaRepository<Vehicle,String>{}
@Entity @Table(name="inventory_change_outbox") class ChangeOutbox { @Id @Column(name="event_id") UUID eventId; @Column(name="vehicle_id") String vehicleId; @Column(name="event_type") String eventType; @Column(name="source_version") long sourceVersion; @Column(name="occurred_at") Instant occurredAt; @Column(name="publication_status") String status; @Column(name="attempt_count") int attempts; @Column(name="published_at") Instant publishedAt; @Column(name="created_at") Instant createdAt; }
interface ChangeOutboxes extends JpaRepository<ChangeOutbox,UUID>{ List<ChangeOutbox> findTop50ByStatusOrderByCreatedAtAsc(String status); }
record Request(@NotBlank String vehicleId,@NotBlank String model,@Min(1886) int modelYear,@NotBlank String market,@NotBlank String powertrain,@NotEmpty List<@NotBlank String> supportedCapabilities){}
/**
 * The original capability fields remain unchanged.  sourceVersion and
 * sourceUpdatedAt are additive metadata used by downstream synchronizers to
 * ignore replayed Inventory change notifications.
 */
record Capability(String vehicleId,String model,int modelYear,String market,String powertrain,List<String> supportedCapabilities,long sourceVersion,Instant sourceUpdatedAt){}
@RestController class InventoryController {
 private final Vehicles vehicles; private final InventoryChanges changes; InventoryController(Vehicles v,InventoryChanges c){vehicles=v;changes=c;}
 @GetMapping("/v1/vehicles/{id}/capability") Capability capability(@PathVariable String id){return view(get(id));}
 @GetMapping("/admin/v1/vehicles") List<Capability> list(){return vehicles.findAll().stream().map(this::view).toList();}
 @GetMapping("/admin/v1/vehicles/{id}") Capability one(@PathVariable String id){return view(get(id));}
 @PostMapping("/admin/v1/vehicles") ResponseEntity<Capability> create(@Valid @RequestBody Request r){return ResponseEntity.status(201).body(view(changes.create(r)));}
 @PutMapping("/admin/v1/vehicles/{id}") Capability update(@PathVariable String id,@Valid @RequestBody Request r){if(!id.equals(r.vehicleId()))throw new IllegalArgumentException("vehicleId mismatch");return view(changes.update(r));}
 private Vehicle get(String id){return vehicles.findById(id).orElseThrow(()->new NoSuchElementException("Vehicle not found"));}
 private Capability view(Vehicle v){return new Capability(v.vehicleId,v.modelCode,v.modelYear,v.marketCode,v.powertrainCode,capabilities(v.supportedCapabilities),v.sourceVersion,v.updatedAt);}
 private List<String> capabilities(String stored){return Arrays.stream(stored.replaceAll("[\\[\\]\"]", "").split("\\s*,\\s*")).filter(value->!value.isBlank()).toList();}
}
@org.springframework.stereotype.Service class InventoryChanges { final Vehicles vehicles; final ChangeOutboxes outbox; InventoryChanges(Vehicles v,ChangeOutboxes o){vehicles=v;outbox=o;} @Transactional Vehicle create(Request r){if(vehicles.existsById(r.vehicleId()))throw new IllegalArgumentException("Vehicle already exists");Vehicle v=vehicles.save(new Vehicle(r.vehicleId(),r,1));intent(v,"CREATED");return v;} @Transactional Vehicle update(Request r){Vehicle v=vehicles.findById(r.vehicleId()).orElseThrow(()->new NoSuchElementException("Vehicle not found"));if(v.modelCode.equals(r.model())&&v.modelYear==r.modelYear()&&v.marketCode.equals(r.market())&&v.powertrainCode.equals(r.powertrain())&&sameCapabilities(v.supportedCapabilities,r.supportedCapabilities()))return v;v.apply(r,v.sourceVersion+1);intent(v,"UPDATED");return vehicles.save(v);} private boolean sameCapabilities(String stored,List<String> requested){return Arrays.stream(stored.replaceAll("[\\[\\]\"]", "").split("\\s*,\\s*")).filter(value->!value.isBlank()).toList().equals(requested);} void intent(Vehicle v,String type){ChangeOutbox e=new ChangeOutbox();e.eventId=UUID.randomUUID();e.vehicleId=v.vehicleId;e.eventType=type;e.sourceVersion=v.sourceVersion;e.occurredAt=v.updatedAt;e.createdAt=Instant.now();e.status="PENDING";outbox.save(e);} }
@org.springframework.stereotype.Component class InventoryOutboxPublisher { final ChangeOutboxes outbox; final KafkaTemplate<String,String> kafka; final com.fasterxml.jackson.databind.ObjectMapper json; InventoryOutboxPublisher(ChangeOutboxes o,KafkaTemplate<String,String> k,com.fasterxml.jackson.databind.ObjectMapper j){outbox=o;kafka=k;json=j;} @Scheduled(fixedDelayString="${inventory.outbox.fixed-delay-ms:1000}") @Transactional void publish(){for(ChangeOutbox e:outbox.findTop50ByStatusOrderByCreatedAtAsc("PENDING"))try{kafka.send("inventory.vehicle-master-changed.v1",e.vehicleId,json.writeValueAsString(java.util.Map.of("eventId",e.eventId,"eventType",e.eventType,"vehicleId",e.vehicleId,"sourceVersion",e.sourceVersion,"occurredAt",e.occurredAt))).get();e.status="PUBLISHED";e.publishedAt=Instant.now();outbox.save(e);}catch(Exception x){e.attempts++;outbox.save(e);}} }
@RestControllerAdvice class Errors { @ExceptionHandler(NoSuchElementException.class) ResponseEntity<Void> notFound(){return ResponseEntity.notFound().build();} @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,String>> bad(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));} }
