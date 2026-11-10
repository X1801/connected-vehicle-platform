package com.cvp.maintenance.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MaintenanceCycleReferenceTest {
    @Test
    void isStableForTheSameMaintenanceOccurrence() {
        String first = MaintenanceCycleReference.calculate("vehicle-1", "OIL_SERVICE", "v1", "EU-A3", 15000L, LocalDate.of(2026, 10, 1));
        String replay = MaintenanceCycleReference.calculate("vehicle-1", "OIL_SERVICE", "v1", "EU-A3", 15000L, LocalDate.of(2026, 10, 1));
        assertEquals(first, replay);
        assertEquals(64, first.length());
    }

    @Test
    void changesWhenDueOccurrenceChanges() {
        assertNotEquals(MaintenanceCycleReference.calculate("v", "OIL", "1", "scope", 100L, null), MaintenanceCycleReference.calculate("v", "OIL", "1", "scope", 101L, null));
    }

    @Test
    void authoritativeReferenceIsStableAcrossMutablePolicyAndDueCorrections() {
        UUID c1 = UUID.randomUUID();
        assertEquals(MaintenanceCycleReference.calculateAuthoritative("v", "OIL", c1),
                MaintenanceCycleReference.calculateAuthoritative("v", "OIL", c1));
        assertNotEquals(MaintenanceCycleReference.calculateAuthoritative("v", "OIL", c1),
                MaintenanceCycleReference.calculateAuthoritative("v", "OIL", UUID.randomUUID()));
    }
}
