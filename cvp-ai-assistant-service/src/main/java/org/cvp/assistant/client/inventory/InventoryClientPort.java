package org.cvp.assistant.client.inventory;

import tools.jackson.databind.JsonNode;

public interface InventoryClientPort {
    JsonNode capability(String vehicleId);
}
