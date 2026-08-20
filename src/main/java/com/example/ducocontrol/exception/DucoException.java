package com.example.ducocontrol.exception;

/**
 * Base class for all errors specific to the Duco integration.
 *
 * Why a dedicated exception hierarchy instead of just
 * `throw new Exception("something went wrong")`? A generic Exception tells
 * the caller nothing about WHAT went wrong -- it would have to read and
 * pattern-match the message string to know whether it was a network problem
 * or invalid input. With specific subclasses (see DucoUnreachableException,
 * DucoInvalidStateException), the HTTP layer further down can just
 * `catch (DucoUnreachableException e)` and know that a 502 (bad gateway) is
 * the right response, while a DucoInvalidStateException justifies a 400
 * (bad request). The exception class itself carries meaning, not just the
 * text inside it.
 *
 * Why `extends RuntimeException` (unchecked) instead of `extends Exception`
 * (checked)? Checked exceptions force every method in the call chain to
 * catch or re-declare them via `throws`. Here we only ever catch
 * DucoException variants in one place (the HTTP handlers in server/), so a
 * checked exception would just spread `throws DucoException` clauses
 * through the whole DucoClient layer without adding any real safety. This
 * is the same trade-off frameworks like Spring make for their own exception
 * hierarchies.
 */
public class DucoException extends RuntimeException {

    public DucoException(String message) {
        super(message);
    }

    public DucoException(String message, Throwable cause) {
        super(message, cause);
    }
}
