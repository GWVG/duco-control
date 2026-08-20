package com.example.ducocontrol.exception;

/**
 * Thrown when a requested ventilation state is invalid: a level outside
 * 1-3, an unsupported duration, or an unexpected "Val" string coming back
 * from the DucoBox that we can't map to a known VentilationState.
 */
public class DucoInvalidStateException extends DucoException {

    public DucoInvalidStateException(String message) {
        super(message);
    }

    public DucoInvalidStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
