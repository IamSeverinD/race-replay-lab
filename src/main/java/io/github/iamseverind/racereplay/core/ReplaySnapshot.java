package io.github.iamseverind.racereplay.core;

import java.util.List;
import java.util.Objects;

/**
 * Immutable replay state for one logical point in time.
 *
 * @param replaySeconds logical replay time
 * @param drivers ordered driver states
 */
public record ReplaySnapshot(
        double replaySeconds,
        List<DriverSnapshot> drivers) {

    /**
     * Validates and defensively copies the replay state.
     */
    public ReplaySnapshot {
        if (!Double.isFinite(replaySeconds) || replaySeconds < 0.0) {
            throw new IllegalArgumentException(
                    "Replay time must be non-negative and finite.");
        }

        drivers = List.copyOf(
                Objects.requireNonNull(drivers, "drivers"));
    }
}
