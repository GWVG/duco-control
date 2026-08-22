package com.example.ducocontrol.model;

import com.example.ducocontrol.exception.DucoInvalidStateException;

/**
 * Maps a ventilation level (1/2/3) to the Val string the Duco API expects.
 * CNT states are permanent (explicitly leave AUTO mode); MAN states are
 * temporary and revert to AUTO after a timer. Percentages (15/50/100) are
 * fixed by the hardware -- there's no freely settable percentage.
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
     * Reverse of val(): translates a raw "Val" from a GET /v2/info/nodes
     * response back to this enum. Throws for "AUTO" and other states that
     * aren't user-settable.
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
     * @param level     1 (low), 2 (medium), or 3 (high)
     * @param permanent true for a CNT (permanent) state, false for MAN (timed)
     */
    public static VentilationState fromLevel(int level, boolean permanent) {
        return switch (level) {
            case 1 -> permanent ? CNT1 : MAN1;
            case 2 -> permanent ? CNT2 : MAN2;
            case 3 -> permanent ? CNT3 : MAN3;
            default -> throw new DucoInvalidStateException(
                    "Level must be 1, 2, or 3, got: " + level);
        };
    }
}
