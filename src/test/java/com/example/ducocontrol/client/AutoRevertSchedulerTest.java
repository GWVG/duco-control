package com.example.ducocontrol.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Written before AutoRevertScheduler existed (TDD red phase). We use a real
// ScheduledExecutorService with very short delays (milliseconds instead of
// the real 60/120 minutes) and a CountDownLatch to wait until the
// background task has actually run -- more reliable than gambling with
// Thread.sleep() and hoping the timing works out.
class AutoRevertSchedulerTest {

    @Test
    void scheduledAction_runsAfterTheDelay() throws InterruptedException {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AutoRevertScheduler scheduler = new AutoRevertScheduler(executor);
        CountDownLatch actionRan = new CountDownLatch(1);

        scheduler.scheduleRevert(actionRan::countDown, 50, TimeUnit.MILLISECONDS);

        // await() blocks until the latch reaches 0 OR the timeout elapses.
        // Returns true if the task fired in time.
        assertTrue(actionRan.await(1, TimeUnit.SECONDS));
        executor.shutdown();
    }

    @Test
    void schedulingANewRevert_cancelsThePreviouslyScheduledOne() throws InterruptedException {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AutoRevertScheduler scheduler = new AutoRevertScheduler(executor);
        AtomicBoolean firstActionRan = new AtomicBoolean(false);
        CountDownLatch secondActionRan = new CountDownLatch(1);

        scheduler.scheduleRevert(() -> firstActionRan.set(true), 150, TimeUnit.MILLISECONDS);
        // Before the first task can fire, we schedule a second one -- it
        // should cancel the first.
        scheduler.scheduleRevert(secondActionRan::countDown, 30, TimeUnit.MILLISECONDS);

        assertTrue(secondActionRan.await(1, TimeUnit.SECONDS));
        // Give the (hopefully canceled) first task plenty of time to fire
        // in case the cancellation didn't work.
        Thread.sleep(300);
        assertFalse(firstActionRan.get());
        executor.shutdown();
    }
}
