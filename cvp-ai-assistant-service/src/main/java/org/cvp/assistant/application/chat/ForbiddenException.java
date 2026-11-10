package org.cvp.assistant.application.chat;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
