package com.cvp.maintenance.adapter.inbound.kafka;

import com.cvp.maintenance.application.InventorySynchronizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class InventoryVehicleChangedListenerTest {
    @Test void forwardsVehicleAndSourceRevisionToAuthoritativeSynchronizer() throws Exception {
        InventorySynchronizationService synchronization = mock(InventorySynchronizationService.class);
        new InventoryVehicleChangedListener(new ObjectMapper(), synchronization)
                .onMessage("{\"eventId\":\"e8c650fa-1db3-4b6d-a919-a68635fbd873\",\"vehicleId\":\"VIN-INV-1\",\"sourceVersion\":2}");
        verify(synchronization).synchronizeIfNewer("VIN-INV-1", 2L);
    }

    @Test void rejectsMissingOrInvalidRevision() {
        InventorySynchronizationService synchronization = mock(InventorySynchronizationService.class);
        InventoryVehicleChangedListener listener = new InventoryVehicleChangedListener(new ObjectMapper(), synchronization);
        assertThrows(IllegalArgumentException.class, () -> listener.onMessage("{\"eventId\":\"e8c650fa-1db3-4b6d-a919-a68635fbd873\",\"vehicleId\":\"VIN-INV-1\",\"sourceVersion\":0}"));
        verifyNoInteractions(synchronization);
    }
}
