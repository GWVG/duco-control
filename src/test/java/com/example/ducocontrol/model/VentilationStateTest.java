package com.example.ducocontrol.model;

import com.example.ducocontrol.exception.DucoInvalidStateException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VentilationStateTest {

    // --- fromLevel: replaces the earlier fromPercentage(int, boolean) with
    // "nearest level" rounding. That rounding existed to accommodate a free
    // percentage (0-100), but research (including Home Assistant's own Duco
    // integration, which goes deeper into the protocol than we do) confirms
    // the DucoBox itself knows nothing about a free percentage -- only three
    // discrete levels (CNT1/2/3 = 15/50/100%). So a free percentage was never
    // a real command we were missing, and that rounding logic solved a
    // problem that doesn't exist at the protocol level. fromLevel therefore
    // asks directly for the level (1, 2, or 3), matching what the three UI
    // buttons offer -- no rounding, no ambiguity possible.

    @Test
    void level1Permanent_mapsToCnt1() {
        assertEquals(VentilationState.CNT1, VentilationState.fromLevel(1, true));
    }

    @Test
    void level2Permanent_mapsToCnt2() {
        assertEquals(VentilationState.CNT2, VentilationState.fromLevel(2, true));
    }

    @Test
    void level3Permanent_mapsToCnt3() {
        assertEquals(VentilationState.CNT3, VentilationState.fromLevel(3, true));
    }

    // "permanent = true" means: the user picked "unlimited" as the duration.
    // That must produce a CNT state (explicitly leaves AUTO and stays active).
    // "permanent = false" (a timed duration, e.g. 30 minutes) must produce
    // the MAN counterpart.
    @Test
    void level1Temporary_mapsToMan1() {
        assertEquals(VentilationState.MAN1, VentilationState.fromLevel(1, false));
    }

    @Test
    void level2Temporary_mapsToMan2() {
        assertEquals(VentilationState.MAN2, VentilationState.fromLevel(2, false));
    }

    @Test
    void level3Temporary_mapsToMan3() {
        assertEquals(VentilationState.MAN3, VentilationState.fromLevel(3, false));
    }

    // Only 1/2/3 are valid -- no more rounding to fall back on for a "wrong"
    // level, so this must be explicitly rejected.
    @Test
    void level0_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class, () -> VentilationState.fromLevel(0, true));
    }

    @Test
    void levelFour_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class, () -> VentilationState.fromLevel(4, true));
    }

    // The reverse direction: every state also knows its own percentage,
    // needed to show the current state in a readable way (see StatusHandler/app.js).
    @Test
    void cnt1_hasPercentage15() {
        assertEquals(15, VentilationState.CNT1.percentage());
    }

    @Test
    void cnt3_hasPercentage100() {
        assertEquals(100, VentilationState.CNT3.percentage());
    }

    // --- fromVal: the reverse of val(), needed to translate a "State.Val"
    // from a GET /v2/info/nodes response back into our enum (see
    // DucoResponseParser). Unaffected by the fromLevel switch -- this is
    // about READING the current state, not setting it.

    @Test
    void fromVal_recognisesCnt2() {
        assertEquals(VentilationState.CNT2, VentilationState.fromVal("CNT2"));
    }

    // "AUTO" (the system controls itself, no fixed manual state) isn't a
    // value you can "set" via our three level buttons, so it's not in this
    // enum -- fromVal must explicitly reject it instead of guessing.
    @Test
    void fromVal_withAuto_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class, () -> VentilationState.fromVal("AUTO"));
    }
}
