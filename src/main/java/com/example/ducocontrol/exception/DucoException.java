package com.example.ducocontrol.exception;

/**
 * Base class for errors specific to the Duco integration. Subclasses (see
 * DucoUnreachableException, DucoInvalidStateException) let the HTTP layer
 * pick the right status code by type instead of parsing message text.
 * Unchecked, since callers only ever need to catch it in one place (the
 * HTTP handlers in server/).
 */
public class DucoException extends RuntimeException {

    public DucoException(String message) {
        super(message);
    }

    public DucoException(String message, Throwable cause) {
        super(message, cause);
    }
}
