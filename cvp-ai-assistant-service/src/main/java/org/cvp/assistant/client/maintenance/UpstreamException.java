package org.cvp.assistant.client.maintenance;

public class UpstreamException extends RuntimeException {
    private final int status;
    private final boolean timeout;
    public UpstreamException(int status, String message) { this(status, message, false); }
    public UpstreamException(int status, String message, boolean timeout) { super(message); this.status = status; this.timeout = timeout; }
    public int status() { return status; }
    public boolean timeout() { return timeout; }
    public static boolean isTimeout(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof java.net.SocketTimeoutException || current instanceof java.net.http.HttpTimeoutException) return true;
        }
        return false;
    }
}
