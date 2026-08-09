package io.github.iamseverind.racereplay.core;

/**
 * Provides replay snapshots for logical replay times.
 *
 * <p>The user interface depends on this contract instead of a concrete
 * replay-data source. Implementations may generate synthetic data or read
 * processed race data.</p>
 */
@FunctionalInterface
public interface ReplaySimulation {

    /**
     * Creates the replay snapshot at one logical point in time.
     *
     * @param replaySeconds non-negative logical replay time
     * @return replay snapshot
     */
    ReplaySnapshot snapshotAt(double replaySeconds);

    /**
     * Returns the available replay duration.
     *
     * <p>Implementations without a fixed duration may return NaN.</p>
     *
     * @return replay duration in seconds or NaN
     */
    default double durationSeconds() {
        return Double.NaN;
    }
}
