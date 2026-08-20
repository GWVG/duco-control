package com.example.ducocontrol.exception;

/**
 * Thrown when the DucoBox cannot be reached: timeout, connection refused,
 * DNS name ("duco.home.arpa") doesn't resolve, etc.
 *
 * The cause (the underlying IOException from java.net.http.HttpClient) is
 * always passed along -- so you never lose the original technical reason
 * while the caller can still react to the Duco-specific type.
 */
public class DucoUnreachableException extends DucoException {

    public DucoUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
