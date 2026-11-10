package org.cvp.assistant.controller.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 128) String vehicleId,
        @NotBlank @Size(max = 4000) String userMessage,
        @Size(max = 128) String conversationId) {
}
