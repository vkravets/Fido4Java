package org.fidonet.io.exception;

/**
 * 
 * @author kreon
 * 
 */
public class ProtocolException extends Exception {

    public ProtocolException() {
    }

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProtocolException(Throwable cause) {
        super(cause);
    }
}
