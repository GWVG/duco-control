package com.example.ducocontrol.model;

import com.example.ducocontrol.exception.DucoInvalidStateException;

/**
 * Decides which Duco command a (level, duration) request from the frontend
 * produces. Level is 1/2/3 (low/medium/high) -- no free percentage, see
 * VentilationState for why that one IS restricted to fixed steps (a
 * hardware limit).
 *
 * Duration is a different story: only 15/30/45 minutes correspond to an
 * actual hardware timer on the DucoBox (native, via MAN/MANx2/MANx3).
 * Every other duration -- including values that aren't round numbers, like
 * 37 minutes -- goes through a permanent CNT state plus our own
 * AutoRevertScheduler, which (unlike the box) accepts an arbitrary delay.
 * There's therefore no technical reason to restrict the frontend to a
 * handful of fixed stops for duration: the duration dial lets you pick any
 * whole minute, and this class routes it to the native timer only on the
 * exact three values that actually have one.
 *
 * Verified via curl against node 1 (2026-08-20):
 *   Val "MAN1"    -> TimeStateRemain 900s  (15 min, the default timer)
 *   Val "MAN1x2"  -> TimeStateRemain 1800s (30 min)
 *   Val "MAN1x3"  -> TimeStateRemain ~2700s (45 min)
 *   Val "MAN1" + a separate "Time" field -> {"Code":4,"Result":"FAILED"} (doesn't exist)
 * The allowed Val enum itself can be queried via GET /v2/action/nodes/1.
 */
public final class VentilationCommandPlanner {

    /** Sentinel value: a duration of 0 minutes means "unlimited" (CNT, no timer). */
    public static final int UNLIMITED_DURATION_MINUTES = 0;

    /**
     * Defensive upper bound, not a hardware limit: caps how long a single
     * scheduled (CNT + AutoRevertScheduler) command can run, so a stray
     * drag on the duration dial or a client bug can't silently lock
     * ventilation at one setting for days. 24 hours comfortably covers
     * every duration the dial is meant to offer.
     */
    public static final int MAX_DURATION_MINUTES = 24 * 60;

    // Static methods only, no state to hold -- a private constructor
    // prevents anyone from accidentally doing
    // "new VentilationCommandPlanner()", which wouldn't achieve anything anyway.
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

        // Not one of the three native timer values: permanent (CNT) state,
        // and the caller (SetVentilationHandler) is responsible for
        // scheduling the revert-to-AUTO via AutoRevertScheduler, using the
        // exact minute count the user chose -- no rounding to a fixed stop.
        VentilationState state = VentilationState.fromLevel(level, true);
        return new VentilationCommand(state.val(), true, durationMinutes);
    }

    /**
     * @return the Val suffix for a native MAN timer ("" for the default
     *         15-minute timer, "x2"/"x3" for 30/45 minutes), or null if
     *         durationMinutes doesn't match any native timer.
     */
    private static String nativeManSuffixFor(int durationMinutes) {
        return switch (durationMinutes) {
            case 15 -> "";
            case 30 -> "x2";
            case 45 -> "x3";
            default -> null;
        };
    }
}
