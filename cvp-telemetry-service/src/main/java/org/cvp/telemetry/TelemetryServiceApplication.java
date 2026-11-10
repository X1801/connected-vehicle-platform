package org.cvp.telemetry;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.boot.autoconfigure.SpringBootApplication
public class TelemetryServiceApplication {
    public static void main(String[] a) {
        org.springframework.boot.SpringApplication.run(TelemetryServiceApplication.class, a);
    }
}

@RestController
@Entity
@Table(name = "telemetry_vehicle_state")
class VehicleState {
    @Id
    @Column(name = "vehicle_id")
    String vehicleId;
    @Column(name = "event_id")
    UUID eventId;
    @Column(name = "event_type")
    String eventType;
    @Column(name = "occurred_at")
    Instant occurredAt;
    @Column(name = "source_version")
    long sourceVersion;
    @Column(name = "source_timestamp")
    Instant sourceTimestamp;
    @Column(name = "odometer_km")
    long odometerKm;
    @OneToMany(mappedBy = "state", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id.indicatorCode")
    Set<ServiceIndicator> indicators = new LinkedHashSet<>();
}

@Entity
@Table(name = "telemetry_service_indicator")
class ServiceIndicator {
    @EmbeddedId
    IndicatorId id;
    @MapsId("vehicleId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    VehicleState state;
    @Column(name = "due_odometer_km")
    Long dueOdometerKm;
    @Column(name = "due_date")
    LocalDate dueDate;
    @Column(name = "remaining_distance_km")
    Long remainingDistanceKm;
    @Column(name = "remaining_days")
    Integer remainingDays;
    String severity;
    @Column(name = "source_timestamp")
    Instant sourceTimestamp;
    @Column(name = "maintenance_cycle_id")
    UUID maintenanceCycleId;
    @Column(name = "last_cycle_advance_key")
    String lastCycleAdvanceKey;
    @Column(name = "last_service_reference")
    String lastServiceReference;

    String code() {
        return id.indicatorCode;
    }
}

@Embeddable
class IndicatorId {
    @Column(name = "vehicle_id")
    String vehicleId;
    @Column(name = "indicator_code")
    String indicatorCode;

    IndicatorId() {
    }

    IndicatorId(String v, String c) {
        vehicleId = v;
        indicatorCode = c;
    }
}

@Entity
@Table(name = "telemetry_published_event")
class PublishedEvent {
    @Id
    @Column(name = "event_id")
    UUID eventId;
    @Column(name = "vehicle_id")
    String vehicleId;
    String topic;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    String payload;
    @Column(name = "occurred_at")
    Instant occurredAt;
    @Column(name = "source_version")
    long sourceVersion;
    @Column(name = "published_at")
    Instant publishedAt;
}

interface VehicleStateRepository extends org.springframework.data.jpa.repository.JpaRepository<VehicleState, String> {
}

interface PublishedEventRepository extends org.springframework.data.jpa.repository.JpaRepository<PublishedEvent, UUID> {
    List<PublishedEvent> findByVehicleIdOrderByPublishedAtAsc(String vehicleId);
}

@Service
class MaintenanceStateService {
    private final VehicleStateRepository states;
    private final PublishedEventRepository events;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final String topic;

    MaintenanceStateService(VehicleStateRepository s, PublishedEventRepository e, KafkaTemplate<String, String> k, ObjectMapper j, @Value("${telemetry.maintenance-state-topic}") String t) {
        states = s;
        events = e;
        kafka = k;
        json = j;
        topic = t;
    }

    @Transactional(readOnly = true)
    StateResponse find(String id) {
        return states.findById(id).map(this::response).orElseThrow(() -> new NotFound("Vehicle not found"));
    }

    @Transactional(readOnly = true)
    List<StateResponse> list() {
        return states.findAll().stream().map(this::response).toList();
    }

    @Transactional
    StateResponse save(String id, StateRequest r) {
        VehicleState s = states.findById(id).orElseGet(VehicleState::new);
        if (s.vehicleId != null && r.sourceVersion() <= s.sourceVersion)
            throw new Conflict("sourceVersion must be greater than the stored state");
        s.vehicleId = id;
        s.eventId = UUID.randomUUID();
        s.eventType = "maintenance-state";
        s.occurredAt = Instant.now();
        s.sourceVersion = r.sourceVersion();
        s.sourceTimestamp = r.sourceTimestamp();
        s.odometerKm = r.odometerKm();
        if (r.serviceIndicators() != null) replace(s, r.serviceIndicators());
        return response(states.save(s));
    }

    @Transactional
    EventResponse publish(String id, Long v, Instant ts) {
        VehicleState s = states.findById(id).orElseThrow(() -> new NotFound("Vehicle not found"));
        EventResponse e = new EventResponse(UUID.randomUUID(), s.eventType, id, Instant.now(), v == null ? s.sourceVersion : v, ts == null ? s.sourceTimestamp : ts, s.odometerKm, indicators(s));
        sendStore(e);
        return e;
    }

    @Transactional(readOnly = true)
    List<EventResponse> history(String id) {
        return events.findByVehicleIdOrderByPublishedAtAsc(id).stream().map(x -> parse(x.payload)).toList();
    }

    @Transactional
    EventResponse replay(UUID id) {
        PublishedEvent p = events.findById(id).orElseThrow(() -> new NotFound("Event not found"));
        EventResponse e = parse(p.payload);
        send(e);
        return e;
    }

    @Transactional
    EventResponse advanceCycle(String id, String code, CycleAdvanceRequest request) {
        VehicleState s = states.findById(id).orElseThrow(() -> new NotFound("Vehicle not found"));
        ServiceIndicator indicator = s.indicators.stream().filter(i -> i.code().equals(code)).findFirst().orElseThrow(() -> new NotFound("Indicator not found"));
        if (request.idempotencyKey().equals(indicator.lastCycleAdvanceKey))
            return new EventResponse(s.eventId, s.eventType, id, s.occurredAt, s.sourceVersion, s.sourceTimestamp, s.odometerKm, indicators(s));
        if (request.expectedSourceVersion() != s.sourceVersion + 1)
            throw new Conflict("expectedSourceVersion must advance the current state exactly once");
        if (request.performedAt().isAfter(Instant.now())) throw new BadRequest("performedAt cannot be in the future");
        indicator.maintenanceCycleId = UUID.randomUUID();
        indicator.lastCycleAdvanceKey = request.idempotencyKey();
        indicator.lastServiceReference = request.serviceReference();
        s.eventId = UUID.randomUUID();
        s.occurredAt = Instant.now();
        s.sourceVersion = request.expectedSourceVersion();
        s.sourceTimestamp = s.occurredAt;
        states.save(s);
        EventResponse e = new EventResponse(UUID.randomUUID(), s.eventType, id, Instant.now(), s.sourceVersion, s.sourceTimestamp, s.odometerKm, indicators(s));
        sendStore(e);
        return e;
    }

    private void replace(VehicleState s, List<IndicatorRequest> values) {
        Map<String, ServiceIndicator> previous = s.indicators.stream().collect(java.util.stream.Collectors.toMap(ServiceIndicator::code, i -> i));
        Set<String> codes = new HashSet<>();
        for (IndicatorRequest i : values)
            if (!codes.add(i.code())) throw new BadRequest("serviceIndicators contains duplicate code " + i.code());
        s.indicators.clear();
        for (IndicatorRequest i : values) {
            ServiceIndicator old = previous.get(i.code());
            ServiceIndicator x = new ServiceIndicator();
            x.id = new IndicatorId(s.vehicleId, i.code());
            x.state = s;
            x.dueOdometerKm = i.dueOdometerKm();
            x.dueDate = i.dueDate();
            x.remainingDistanceKm = i.remainingDistanceKm();
            x.remainingDays = i.remainingDays();
            x.severity = i.severity();
            x.sourceTimestamp = i.sourceTimestamp();
            x.maintenanceCycleId = old == null ? UUID.randomUUID() : old.maintenanceCycleId;
            x.lastCycleAdvanceKey = old == null ? null : old.lastCycleAdvanceKey;
            x.lastServiceReference = old == null ? null : old.lastServiceReference;
            s.indicators.add(x);
        }
    }

    private void sendStore(EventResponse e) {
        try {
            send(e);
            PublishedEvent p = new PublishedEvent();
            p.eventId = e.eventId();
            p.vehicleId = e.vehicleId();
            p.topic = topic;
            p.payload = json.writeValueAsString(e);
            p.occurredAt = e.occurredAt();
            p.sourceVersion = e.sourceVersion();
            p.publishedAt = Instant.now();
            events.save(p);
        } catch (UpstreamFailure x) {
            throw x;
        } catch (Exception x) {
            throw new UpstreamFailure("Kafka publication failed: " + x.getMessage());
        }
    }

    private void send(EventResponse e) {
        try {
            kafka.send(topic, e.vehicleId(), json.writeValueAsString(e)).get();
        } catch (Exception x) {
            throw new UpstreamFailure("Kafka publication failed: " + x.getMessage());
        }
    }

    private EventResponse parse(String x) {
        try {
            return json.readValue(x, EventResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private StateResponse response(VehicleState s) {
        return new StateResponse(s.eventId, s.eventType, s.vehicleId, s.occurredAt, s.sourceVersion, s.sourceTimestamp, s.odometerKm, indicators(s));
    }

    private List<IndicatorResponse> indicators(VehicleState s) {
        return s.indicators.stream().map(i -> new IndicatorResponse(i.code(), i.dueOdometerKm, i.dueDate, i.remainingDistanceKm, i.remainingDays, i.severity, i.sourceTimestamp, i.maintenanceCycleId)).toList();
    }
}

@RestController
class TelemetryController {
    private final MaintenanceStateService service;
    private final boolean enabled;

    TelemetryController(MaintenanceStateService s, @Value("${telemetry.management-enabled}") boolean e) {
        service = s;
        enabled = e;
    }

    @GetMapping("/v1/vehicles/{id}/maintenance-state")
    StateResponse get(@PathVariable String id) {
        return service.find(id);
    }

    @GetMapping("/mock/v1/vehicles")
    List<StateResponse> list() {
        check();
        return service.list();
    }

    @PutMapping("/mock/v1/vehicles/{id}/maintenance-state")
    StateResponse put(@PathVariable @NotBlank String id, @Valid @RequestBody StateRequest r) {
        check();
        return service.save(id, r);
    }

    @PostMapping("/mock/v1/vehicles/{id}/maintenance-events")
    EventResponse publish(@PathVariable String id, @RequestParam(required = false) @Positive Long sourceVersion, @RequestParam(required = false) Instant sourceTimestamp) {
        check();
        return service.publish(id, sourceVersion, sourceTimestamp);
    }

    @PostMapping("/mock/v1/vehicles/{id}/maintenance-cycles/{code}/advance")
    EventResponse advance(@PathVariable String id, @PathVariable @NotBlank String code, @Valid @RequestBody CycleAdvanceRequest r) {
        check();
        return service.advanceCycle(id, code, r);
    }

    @GetMapping("/mock/v1/vehicles/{id}/maintenance-events")
    List<EventResponse> history(@PathVariable String id) {
        check();
        return service.history(id);
    }

    @PostMapping("/mock/v1/maintenance-events/{id}/replay")
    EventResponse replay(@PathVariable UUID id) {
        check();
        return service.replay(id);
    }

    private void check() {
        if (!enabled) throw new NotFound("Management API is disabled");
    }
}

record StateRequest(@PositiveOrZero long odometerKm, @Positive long sourceVersion, @NotNull Instant sourceTimestamp,
                    List<@Valid IndicatorRequest> serviceIndicators) {
}

record IndicatorRequest(@NotBlank String code, @PositiveOrZero Long dueOdometerKm, LocalDate dueDate,
                        @PositiveOrZero Long remainingDistanceKm, @PositiveOrZero Integer remainingDays,
                        @NotBlank String severity, @NotNull Instant sourceTimestamp) {
}

record CycleAdvanceRequest(@NotBlank String serviceReference, @NotBlank String idempotencyKey,
                           @NotNull Instant performedAt, @Positive long expectedSourceVersion) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record StateResponse(UUID eventId, String eventType, String vehicleId, Instant occurredAt, long sourceVersion,
                     Instant sourceTimestamp, long odometerKm, List<IndicatorResponse> serviceIndicators) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record EventResponse(UUID eventId, String eventType, String vehicleId, Instant occurredAt, long sourceVersion,
                     Instant sourceTimestamp, long odometerKm, List<IndicatorResponse> serviceIndicators) {
}

record IndicatorResponse(String code, Long dueOdometerKm, LocalDate dueDate, Long remainingDistanceKm,
                         Integer remainingDays, String severity, Instant sourceTimestamp, UUID maintenanceCycleId) {
}

class NotFound extends RuntimeException {
    NotFound(String m) {
        super(m);
    }
}

class Conflict extends RuntimeException {
    Conflict(String m) {
        super(m);
    }
}

class BadRequest extends RuntimeException {
    BadRequest(String m) {
        super(m);
    }
}

class UpstreamFailure extends RuntimeException {
    UpstreamFailure(String m) {
        super(m);
    }
}

@RestControllerAdvice
class Errors {
    @ExceptionHandler(NotFound.class)
    ResponseEntity<Map<String, String>> n(NotFound e) {
        return r(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(Conflict.class)
    ResponseEntity<Map<String, String>> c(Conflict e) {
        return r(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler({BadRequest.class, org.springframework.web.bind.MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, String>> b(Exception e) {
        return r(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(UpstreamFailure.class)
    ResponseEntity<Map<String, String>> u(UpstreamFailure e) {
        return r(HttpStatus.BAD_GATEWAY, e);
    }

    private ResponseEntity<Map<String, String>> r(HttpStatus s, Exception e) {
        return ResponseEntity.status(s).body(Map.of("error", e.getMessage()));
    }
}
