package com.example.ducocontrol.model;

import com.example.ducocontrol.exception.DucoInvalidStateException;

/**
 * Decides which Duco command a (level, duration) request produces. Only
 * 15/30/45-minute durations map to a native DucoBox timer (MAN/MANx2/MANx3);
 * every other duration uses a permanent CNT state plus AutoRevertScheduler,
 * which accepts an arbitrary delay.
 */
public final class VentilationCommandPlanner {

    /** Sentinel value: a duration of 0 minutes means "unlimited" (CNT, no timer). */
    public static final int UNLIMITED_DURATION_MINUTES = 0;

    /** Defensive upper bound (not a hardware limit) on a single scheduled duration. */
    public static final int MAX_DURATION_MINUTES = 24 * 60;

    private VentilationCommandPlanner() {
    }

    public static VentilationCommand plan(int level, int durationMinutes) {
        if (durationMinutes == UNLIMITED_DURATION_MINUTES) {
            VentilationState state = VentilationState.fromLevel(level, true);
            return new VentilationCommand(state.val(), false, 0);
        }
        if (durationMinutes < 0 || durationMinutes > MAX_DURATION_MINUTES) {
            throw new DucoInvalidStateException(
                    "Duration (minutes) must be between 0 (unlimited) and "
                            + MAX_DURATION_MINUTES + ", got: " + durationMinutes);
        }

        String nativeSuffix = nativeManSuffixFor(durationMinutes);
        if (nativeSuffix != null) {
            VentilationState state = VentilationState.fromLevel(level, false);
            return new VentilationCommand(state.val() + nativeSuffix, false, 0);
        }

        VentilationState state = VentilationState.fromLevel(level, true);
        return new VentilationCommand(state.val(), true, durationMinutes);
    }

    /** @return the Val suffix for a native MAN timer, or null if none matches. */
    private static String nativeManSuffixFor(int durationMinutes) {
        return switch (durationMinutes) {
            case 15 -> "";
            case 30 -> "x2";
            case 45 -> "x3";
            default -> null;
        };
    }
}
