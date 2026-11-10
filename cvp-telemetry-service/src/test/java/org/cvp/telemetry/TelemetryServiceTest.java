package org.cvp.telemetry;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TelemetryServiceTest {
  @Test void stateRequestCarriesContractFields() {
    var indicator = new IndicatorRequest("OIL",15000L,LocalDate.now().plusDays(30),750L,30,"WARNING",Instant.now());
    var request = new StateRequest(14250,1,Instant.now(),List.of(indicator));
    assertEquals("OIL",request.serviceIndicators().getFirst().code());
    assertEquals(1,request.sourceVersion());
  }

  @Test void cycleAdvanceIsExplicitPerIndicatorAndIdempotent() {
    VehicleState state = new VehicleState();
    state.vehicleId = "TEST-C2";
    state.eventId = UUID.randomUUID();
    state.eventType = "maintenance-state";
    state.occurredAt = Instant.now().minusSeconds(60);
    state.sourceTimestamp = state.occurredAt;
    state.sourceVersion = 4;
    state.odometerKm = 20000;
    ServiceIndicator oil = new ServiceIndicator();
    oil.id = new IndicatorId("TEST-C2", "OIL"); oil.state = state; oil.maintenanceCycleId = UUID.randomUUID();
    ServiceIndicator brake = new ServiceIndicator();
    brake.id = new IndicatorId("TEST-C2", "BRAKE"); brake.state = state; brake.maintenanceCycleId = UUID.randomUUID();
    state.indicators.add(oil); state.indicators.add(brake);
    UUID c1 = oil.maintenanceCycleId, brakeCycle = brake.maintenanceCycleId;
    VehicleStateRepository states = mock(VehicleStateRepository.class);
    PublishedEventRepository events = mock(PublishedEventRepository.class);
    @SuppressWarnings("unchecked") KafkaTemplate<String,String> kafka = mock(KafkaTemplate.class);
    when(states.findById("TEST-C2")).thenReturn(Optional.of(state));
    when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    MaintenanceStateService service = new MaintenanceStateService(states, events, kafka,
        new ObjectMapper().findAndRegisterModules(), "telemetry.vehicle-maintenance-state.v1");
    CycleAdvanceRequest request = new CycleAdvanceRequest("SERVICE-C2", "advance-key", Instant.now().minusSeconds(1), 5);

    EventResponse advanced = service.advanceCycle("TEST-C2", "OIL", request);
    EventResponse repeated = service.advanceCycle("TEST-C2", "OIL", request);

    assertNotEquals(c1, oil.maintenanceCycleId);
    assertEquals(brakeCycle, brake.maintenanceCycleId);
    assertEquals(5, advanced.sourceVersion());
    assertEquals(advanced.sourceVersion(), repeated.sourceVersion());
    verify(events, times(1)).save(any(PublishedEvent.class));
    verify(kafka, times(1)).send(anyString(), anyString(), anyString());
  }
}
