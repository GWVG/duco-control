package com.example.ducocontrol.model;

import com.example.ducocontrol.exception.DucoInvalidStateException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VentilationCommandPlannerTest {

    @Test
    void unlimitedDuration_sendsPermanentCntState_noRevertScheduled() {
        VentilationCommand command = VentilationCommandPlanner.plan(1, 0);
        assertEquals("CNT1", command.val());
        assertFalse(command.requiresScheduledRevert());
    }

    @Test
    void fifteenMinutes_sendsNativeManState_noRevertScheduled() {
        VentilationCommand command = VentilationCommandPlanner.plan(1, 15);
        assertEquals("MAN1", command.val());
        assertFalse(command.requiresScheduledRevert());
    }

    @Test
    void thirtyMinutes_sendsManStateWithX2Suffix_noRevertScheduled() {
        VentilationCommand command = VentilationCommandPlanner.plan(1, 30);
        assertEquals("MAN1x2", command.val());
        assertFalse(command.requiresScheduledRevert());
    }

    @Test
    void fortyFiveMinutes_sendsManStateWithX3Suffix_noRevertScheduled() {
        VentilationCommand command = VentilationCommandPlanner.plan(1, 45);
        assertEquals("MAN1x3", command.val());
        assertFalse(command.requiresScheduledRevert());
    }

    @Test
    void oneHour_sendsPermanentCntState_andSchedulesRevertAfter60Minutes() {
        VentilationCommand command = VentilationCommandPlanner.plan(1, 60);
        assertEquals("CNT1", command.val());
        assertTrue(command.requiresScheduledRevert());
        assertEquals(60, command.revertAfterMinutes());
    }

    @Test
    void twoHours_atLevel2_sendsPermanentCnt2_andSchedulesRevertAfter120Minutes() {
        VentilationCommand command = VentilationCommandPlanner.plan(2, 120);
        assertEquals("CNT2", command.val());
        assertTrue(command.requiresScheduledRevert());
        assertEquals(120, command.revertAfterMinutes());
    }

    @Test
    void arbitraryDuration_sendsPermanentCntState_andSchedulesRevertAfterExactMinutes() {
        VentilationCommand command = VentilationCommandPlanner.plan(1, 37);
        assertEquals("CNT1", command.val());
        assertTrue(command.requiresScheduledRevert());
        assertEquals(37, command.revertAfterMinutes());
    }

    @Test
    void durationJustAboveNativeThreshold_isNotRoundedDown() {
        VentilationCommand command = VentilationCommandPlanner.plan(1, 46);
        assertEquals("CNT1", command.val());
        assertTrue(command.requiresScheduledRevert());
        assertEquals(46, command.revertAfterMinutes());
    }

    @Test
    void negativeDuration_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class,
                () -> VentilationCommandPlanner.plan(1, -5));
    }

    @Test
    void durationAboveMaximum_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class,
                () -> VentilationCommandPlanner.plan(1, 1441));
    }

    @Test
    void durationAtMaximum_isAccepted() {
        VentilationCommand command = VentilationCommandPlanner.plan(1, 1440);
        assertTrue(command.requiresScheduledRevert());
        assertEquals(1440, command.revertAfterMinutes());
    }

    @Test
    void unsupportedLevel_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class,
                () -> VentilationCommandPlanner.plan(4, 15));
    }
}
