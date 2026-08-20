package com.example.ducocontrol.model;

import com.example.ducocontrol.exception.DucoInvalidStateException;

/**
 * Models the ventilation states the DucoBox understands, and the mapping
 * between a level (1/2/3, chosen via the three level buttons in the UI) and
 * the "Val" string the Duco REST API expects in the POST /set body.
 *
 * CNT states (CNT1/2/3) are continuous/permanent: they explicitly leave AUTO
 * mode and stay active until someone changes them again. MAN states
 * (MAN1/2/3) are temporary: the DucoBox falls back to AUTO after a timer.
 * See also the note in DucoClient about the quirk that AUTO mode overwrites
 * a manual setting every cycle -- that's why we always explicitly choose
 * between CNT (permanent) and MAN (temporary) based on what the user
 * selected in the duration field.
 *
 * Percentages below were verified by posting live against node 1 and
 * reading "FlowLvlTgt" in GET /v2/info/nodes (2026-08-20):
 *   CNT1 -> FlowLvlTgt 15   CNT2 -> FlowLvlTgt 50   CNT3 -> FlowLvlTgt 100
 * These are the three standard Duco levels (low/medium/high), not a guess
 * on our part. There is NO freely settable percentage at the protocol
 * level -- confirmed by Home Assistant's own Duco integration (which goes
 * deeper into the protocol than we do): "Setting a speed percentage to 33%,
 * 66%, or 100% activates a continuous override with no time limit." That's
 * why this class works directly with discrete levels (fromLevel) instead of
 * a free percentage that gets rounded afterwards -- that rounding would
 * suggest a capability the hardware doesn't have.
 */
public enum VentilationState {

    CNT1(15, "CNT1"),
    CNT2(50, "CNT2"),
    CNT3(100, "CNT3"),
    MAN1(15, "MAN1"),
    MAN2(50, "MAN2"),
    MAN3(100, "MAN3");

    private final int percentage;
    private final String val;

    VentilationState(int percentage, String val) {
        this.percentage = percentage;
        this.val = val;
    }

    public int percentage() {
        return percentage;
    }

    public String val() {
        return val;
    }

    /**
     * The reverse of val(): translates a raw "Val" string from a GET
     * /v2/info/nodes response back to our enum. Deliberately fails on
     * "AUTO" (and on AUT1/AUT2/AUT3, EMPT, and the x2/x3 duration suffixes
     * you only use when sending, never get back when reading) -- those
     * aren't states you can pick via the three level buttons.
     */
    public static VentilationState fromVal(String val) {
        for (VentilationState state : values()) {
            if (state.val.equals(val)) {
                return state;
            }
        }
        throw new DucoInvalidStateException("Unknown or non-settable ventilation state: " + val);
    }

    /**
     * Translates a chosen level + "should this stick around?" into the
     * exact VentilationState. No rounding or distance calculation (like an
     * earlier version of this class did): the three UI buttons always send
     * 1, 2, or 3 directly, so any other value is an error, not a "closest
     * valid choice" to fall back to.
     *
     * @param level     1 (low/15%), 2 (medium/50%), or 3 (high/100%)
     * @param permanent true if the user picked "unlimited" (-> CNT state,
     *                  leaves AUTO), false for a timed duration (-> MAN
     *                  state, falls back to AUTO after its timer)
     */
    public static VentilationState fromLevel(int level, boolean permanent) {
        // A "switch expression" (since Java 14): unlike a classic switch
        // statement, every branch here produces a value (with "->", no
        // "break" needed, no fall-through bugs possible).
        return switch (level) {
            case 1 -> permanent ? CNT1 : MAN1;
            case 2 -> permanent ? CNT2 : MAN2;
            case 3 -> permanent ? CNT3 : MAN3;
            default -> throw new DucoInvalidStateException(
                    "Level must be 1, 2, or 3, got: " + level);
        };
    }
}
