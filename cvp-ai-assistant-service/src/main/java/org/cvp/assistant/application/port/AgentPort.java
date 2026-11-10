package org.cvp.assistant.application.port;

import org.cvp.assistant.application.chat.ChatCommand;
import org.cvp.assistant.application.chat.ChatResult;

public interface AgentPort {
    ChatResult answer(ChatCommand command, String conversationSummary);
}
