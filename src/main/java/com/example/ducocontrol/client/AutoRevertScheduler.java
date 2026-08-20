package com.example.ducocontrol.client;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules a one-shot background task ("set ventilation back to AUTO") and
 * makes sure there's never more than one pending at a time. Needed because
 * the DucoBox itself has no 1h/2h timer (only 15/30 min via MAN(x2), see
 * VentilationCommandPlanner) -- for those durations we simulate the timer
 * ourselves here.
 *
 * The ScheduledExecutorService is passed in from outside (constructor
 * injection) instead of created here. Same reasoning as DucoHttpGateway: in
 * production a single, shared executor runs (created in DucoControlApp),
 * and in tests you can pass in your own executor with short delays without
 * this class needing to know the difference.
 */
public class AutoRevertScheduler {

    private final ScheduledExecutorService executor;

    // The "ticket" for the currently scheduled task, or null if there is
    // none. Not final: gets replaced every time a new revert is scheduled.
    private ScheduledFuture<?> pendingRevert;

    public AutoRevertScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    /**
     * Schedules revertAction to run after the given delay, first canceling
     * any previously scheduled task that hasn't run yet. "synchronized"
     * because /set requests can arrive concurrently from multiple HTTP
     * threads (com.sun.net.httpserver.HttpServer processes requests on a
     * thread pool) -- without this lock, two concurrent calls could
     * overwrite each other's pendingRevert field and "lose" a task that
     * then never gets canceled.
     */
    public synchronized void scheduleRevert(Runnable revertAction, long delay, TimeUnit unit) {
        cancelPending();
        pendingRevert = executor.schedule(revertAction, delay, unit);
    }

    /** Cancels the pending task, if there is one. No-op otherwise. */
    public synchronized void cancelPending() {
        if (pendingRevert != null) {
            // false = let an already-running task finish; we only want to
            // stop tasks that haven't started yet.
            pendingRevert.cancel(false);
            pendingRevert = null;
        }
    }
}
