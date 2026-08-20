package com.example.ducocontrol.model;

import com.example.ducocontrol.exception.DucoInvalidStateException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// This class decides how a (level, duration) pair from the frontend
// translates into a concrete Duco command, based on what we verified via
// curl:
//  - 15/30/45 min -> the box's own native MAN timer (MAN, MANx2, MANx3) --
//    the ONLY three durations that correspond to an actual hardware timer.
//  - any other duration (37 min, 90 min, ...) -> the box doesn't support
//    this natively; we send a permanent CNT state and schedule a "revert to
//    AUTO" command ourselves via AutoRevertScheduler, which -- unlike the
//    box's own timer -- accepts an arbitrary delay. There's no technical
//    reason to restrict the frontend to a handful of fixed stops here, so
//    it doesn't: the duration dial lets you pick any whole minute.
//  - unlimited (duration = 0, our agreed sentinel value) -> CNT, no
//    scheduled revert
// Level is 1/2/3 (low/medium/high) instead of a free percentage -- see
// VentilationStateTest for why THAT one stays fixed (a hardware limit,
// unlike duration).
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

    // 45 min is a native duration too (MAN1x3, verified: TimeStateRemain
    // ~2700s) -- previously not reachable from the fixed five-option
    // dropdown, but the free-dragging dial can land on it exactly.
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

    // The actual point of this design: a duration that isn't any of the
    // old fixed stops must still work, using the exact minute count chosen.
    @Test
    void arbitraryDuration_sendsPermanentCntState_andSchedulesRevertAfterExactMinutes() {
        VentilationCommand command = VentilationCommandPlanner.plan(1, 37);
        assertEquals("CNT1", command.val());
        assertTrue(command.requiresScheduledRevert());
        assertEquals(37, command.revertAfterMinutes());
    }

    // Guards against an accidental "round to nearest native stop" bug: 46
    // must NOT quietly become 45.
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

    // Defensive upper bound (24h) -- not a hardware limit, just guards
    // against a stray drag or bug locking ventilation at one setting for
    // days without anyone noticing. See VentilationCommandPlanner.
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

    // Analogous: the three UI buttons only ever send 1/2/3, but a tampered
    // request could send something else -- must be rejected here too.
    // (This validation lives in VentilationState.fromLevel, but plan() calls
    // it, so this confirms the error propagates through this path as well.)
    @Test
    void unsupportedLevel_throwsDucoInvalidStateException() {
        assertThrows(DucoInvalidStateException.class,
                () -> VentilationCommandPlanner.plan(4, 15));
    }
}
