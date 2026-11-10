package org.cvp.assistant.application.chat;

public record ChatCommand(String principal, String vehicleId, String userMessage, String conversationId,
                          String correlationId) {
}
