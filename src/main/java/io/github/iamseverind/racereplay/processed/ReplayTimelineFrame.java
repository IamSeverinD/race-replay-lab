package io.github.iamseverind.racereplay.processed;

import java.util.List;
import java.util.Objects;

/**
 * Complete decoded state of one replay timeline frame.
 *
 * @param elapsedMillis elapsed replay time in milliseconds
 * @param drivers driver states in the fixed header order
 */
public record ReplayTimelineFrame(
        int elapsedMillis,
        List<ReplayDriverState> drivers) {

    /**
     * Validates and defensively copies the frame.
     */
    public ReplayTimelineFrame {
        if (elapsedMillis < 0) {
            throw new IllegalArgumentException(
                    "Elapsed milliseconds must not be negative.");
        }

        drivers =
                List.copyOf(
                        Objects.requireNonNull(
                                drivers,
                                "drivers"));

        if (drivers.isEmpty()) {
            throw new IllegalArgumentException(
                    "A timeline frame must contain drivers.");
        }
    }
}
