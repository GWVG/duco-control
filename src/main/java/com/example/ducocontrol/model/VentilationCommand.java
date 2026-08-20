package com.example.ducocontrol.model;

/**
 * The result of VentilationCommandPlanner: exactly what to send to the
 * DucoBox, and whether our own backend needs to schedule a "revert to
 * AUTO" command afterwards (for durations the box doesn't support
 * natively, like 1h/2h -- see VentilationCommandPlanner).
 *
 * This is a "record": an immutable data bundle. The compiler automatically
 * generates the constructor, the getters (val(), not getVal()), and correct
 * equals()/hashCode()/toString(). Useful here since this object is pure
 * data with no behavior of its own -- no reason to hand-write that.
 */
public record VentilationCommand(String val, boolean requiresScheduledRevert, int revertAfterMinutes) {
}
