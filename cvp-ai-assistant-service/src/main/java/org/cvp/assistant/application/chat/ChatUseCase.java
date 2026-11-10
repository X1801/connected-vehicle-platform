package org.cvp.assistant.application.chat;

public interface ChatUseCase {
    ChatResult chat(ChatCommand command);
}
