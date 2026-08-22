package com.example.ducocontrol.client;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules a one-shot "set ventilation back to AUTO" task, canceling any
 * previously scheduled one so there's never more than one pending. Used for
 * durations the DucoBox has no native timer for (see VentilationCommandPlanner).
 */
public class AutoRevertScheduler {

    private final ScheduledExecutorService executor;

    private ScheduledFuture<?> pendingRevert;

    public AutoRevertScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    /** Schedules revertAction after the given delay; synchronized since /set requests can arrive concurrently. */
    public synchronized void scheduleRevert(Runnable revertAction, long delay, TimeUnit unit) {
        cancelPending();
        pendingRevert = executor.schedule(revertAction, delay, unit);
    }

    /** Cancels the pending task, if there is one. */
    public synchronized void cancelPending() {
        if (pendingRevert != null) {
            pendingRevert.cancel(false);
            pendingRevert = null;
        }
    }
}
