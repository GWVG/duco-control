package com.example.ducocontrol.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoRevertSchedulerTest {

    @Test
    void scheduledAction_runsAfterTheDelay() throws InterruptedException {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        AutoRevertScheduler scheduler = new AutoRevertScheduler(executor);
        CountDownLatch actionRan = new CountDownLatch(1);

        scheduler.scheduleRevert(actionRan::countDown, 50, TimeUnit.MILLISECONDS);

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
        scheduler.scheduleRevert(secondActionRan::countDown, 30, TimeUnit.MILLISECONDS);

        assertTrue(secondActionRan.await(1, TimeUnit.SECONDS));
        Thread.sleep(300);
        assertFalse(firstActionRan.get());
        executor.shutdown();
    }
}
