package com.example.ducocontrol.model;

import com.example.ducocontrol.exception.DucoInvalidStateException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VentilationStateTest {

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

    @Test
    void level0_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class, () -> VentilationState.fromLevel(0, true));
    }

    @Test
    void levelFour_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class, () -> VentilationState.fromLevel(4, true));
    }

    @Test
    void cnt1_hasPercentage15() {
        assertEquals(15, VentilationState.CNT1.percentage());
    }

    @Test
    void cnt3_hasPercentage100() {
        assertEquals(100, VentilationState.CNT3.percentage());
    }

    @Test
    void fromVal_recognisesCnt2() {
        assertEquals(VentilationState.CNT2, VentilationState.fromVal("CNT2"));
    }

    @Test
    void fromVal_withAuto_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class, () -> VentilationState.fromVal("AUTO"));
    }
}
