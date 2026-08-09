package io.github.iamseverind.racereplay.core;

import java.util.concurrent.CancellationException;

/**
 * Provides cooperative cancellation checkpoints for long-running work.
 */
public final class CancellationSupport {

    private CancellationSupport() {
    }

    /**
     * Stops the current operation when its thread has been interrupted.
     *
     * <p>The interrupt flag remains set so callers and task frameworks can
     * continue to observe the cancellation.</p>
     *
     * @throws CancellationException when the current thread is interrupted
     */
    public static void checkpoint() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException(
                    "Operation was cancelled.");
        }
    }
}
