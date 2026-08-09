package io.github.iamseverind.racereplay.processed;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Header metadata for one binary replay timeline.
 *
 * @param formatVersion binary format version
 * @param replayStart absolute replay start
 * @param frameIntervalMillis frame interval in milliseconds
 * @param frameCount number of frames
 * @param driverNumbers fixed driver order
 */
public record ReplayTimelineHeader(
        int formatVersion,
        Instant replayStart,
        int frameIntervalMillis,
        int frameCount,
        List<Integer> driverNumbers) {

    /**
     * Validates and copies header values.
     */
    public ReplayTimelineHeader {
        if (formatVersion
                != ReplayTimelineFormat.VERSION) {

            throw new IllegalArgumentException(
                    "Unsupported timeline format version: "
                    + formatVersion);
        }

        replayStart =
                Objects.requireNonNull(
                        replayStart,
                        "replayStart");

        if (frameIntervalMillis <= 0) {
            throw new IllegalArgumentException(
                    "Frame interval must be positive.");
        }

        if (frameCount <= 0) {
            throw new IllegalArgumentException(
                    "Frame count must be positive.");
        }

        driverNumbers =
                List.copyOf(
                        Objects.requireNonNull(
                                driverNumbers,
                                "driverNumbers"));

        if (driverNumbers.isEmpty()) {
            throw new IllegalArgumentException(
                    "Driver numbers must not be empty.");
        }

        int previousDriverNumber = 0;

        for (final int driverNumber : driverNumbers) {
            if (driverNumber <= 0) {
                throw new IllegalArgumentException(
                        "Driver numbers must be positive.");
            }

            if (driverNumber <= previousDriverNumber) {
                throw new IllegalArgumentException(
                        "Driver numbers must be unique and sorted.");
            }

            previousDriverNumber =
                    driverNumber;
        }
    }

    /**
     * Returns the number of drivers.
     *
     * @return driver count
     */
    public int driverCount() {
        return driverNumbers.size();
    }

    /**
     * Returns the complete header size.
     *
     * @return header size in bytes
     */
    public int headerSizeBytes() {
        return ReplayTimelineFormat
                .headerSizeBytes(driverCount());
    }

    /**
     * Returns one frame's size.
     *
     * @return frame size in bytes
     */
    public int frameSizeBytes() {
        return ReplayTimelineFormat
                .frameSizeBytes(driverCount());
    }

    /**
     * Returns the complete expected timeline size.
     *
     * @return timeline size in bytes
     */
    public long expectedFileSizeBytes() {
        return ReplayTimelineFormat
                .expectedFileSizeBytes(
                        frameCount,
                        driverCount());
    }
}
