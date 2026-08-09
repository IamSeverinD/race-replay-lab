package io.github.iamseverind.racereplay.processed;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Result of building or reusing a binary replay timeline.
 *
 * @param timelineFile completed timeline file
 * @param reused whether an existing valid file was reused
 * @param bytes timeline size
 * @param sha256 timeline SHA-256 checksum
 * @param frameCount frame count
 * @param driverCount driver count
 * @param totalStates total driver-state count
 * @param locationValidStates states with valid location
 * @param telemetryValidStates states with valid telemetry
 * @param fullyValidStates states with location and telemetry
 * @param sourceInvalidTelemetryRecords raw telemetry records rejected
 */
public record ReplayTimelineBuildResult(
        Path timelineFile,
        boolean reused,
        long bytes,
        String sha256,
        int frameCount,
        int driverCount,
        long totalStates,
        long locationValidStates,
        long telemetryValidStates,
        long fullyValidStates,
        long sourceInvalidTelemetryRecords) {

    /**
     * Validates timeline build statistics.
     */
    public ReplayTimelineBuildResult {
        timelineFile =
                Objects.requireNonNull(
                        timelineFile,
                        "timelineFile");

        sha256 =
                Objects.requireNonNull(
                        sha256,
                        "sha256");

        if (sha256.length() != 64) {
            throw new IllegalArgumentException(
                    "SHA-256 must contain 64 characters.");
        }

        if (bytes <= 0
                || frameCount <= 0
                || driverCount <= 0
                || totalStates <= 0) {

            throw new IllegalArgumentException(
                    "Timeline totals must be positive.");
        }

        if (locationValidStates < 0
                || telemetryValidStates < 0
                || fullyValidStates < 0
                || sourceInvalidTelemetryRecords < 0
                || locationValidStates > totalStates
                || telemetryValidStates > totalStates
                || fullyValidStates > totalStates) {

            throw new IllegalArgumentException(
                    "Invalid timeline validity statistics.");
        }
    }
}
