package com.example.ducocontrol.model;

/**
 * The result of VentilationCommandPlanner: what to send to the DucoBox, and
 * whether AutoRevertScheduler needs to schedule a revert to AUTO afterwards.
 */
public record VentilationCommand(String val, boolean requiresScheduledRevert, int revertAfterMinutes) {
}
