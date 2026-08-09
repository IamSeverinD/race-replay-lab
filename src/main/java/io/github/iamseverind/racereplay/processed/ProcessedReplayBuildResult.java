package io.github.iamseverind.racereplay.processed;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Result of the processed replay metadata build.
 *
 * @param processedDirectory processed cache directory
 * @param manifestFile processed replay manifest
 * @param driversFile normalized driver metadata
 * @param replayStart first usable replay timestamp
 * @param replayEnd final usable replay timestamp
 * @param frameIntervalMillis planned timeline interval
 * @param frameCount planned timeline frame count
 * @param driverCount normalized driver count
 */
public record ProcessedReplayBuildResult(
        Path processedDirectory,
        Path manifestFile,
        Path driversFile,
        Instant replayStart,
        Instant replayEnd,
        long frameIntervalMillis,
        long frameCount,
        int driverCount) {

    /**
     * Validates the build result.
     */
    public ProcessedReplayBuildResult {
        processedDirectory =
                Objects.requireNonNull(
                        processedDirectory,
                        "processedDirectory");

        manifestFile =
                Objects.requireNonNull(
                        manifestFile,
                        "manifestFile");

        driversFile =
                Objects.requireNonNull(
                        driversFile,
                        "driversFile");

        replayStart =
                Objects.requireNonNull(
                        replayStart,
                        "replayStart");

        replayEnd =
                Objects.requireNonNull(
                        replayEnd,
                        "replayEnd");

        if (!replayStart.isBefore(replayEnd)) {
            throw new IllegalArgumentException(
                    "Replay start must precede replay end.");
        }

        if (frameIntervalMillis <= 0) {
            throw new IllegalArgumentException(
                    "Frame interval must be positive.");
        }

        if (frameCount <= 0) {
            throw new IllegalArgumentException(
                    "Frame count must be positive.");
        }

        if (driverCount <= 0) {
            throw new IllegalArgumentException(
                    "Driver count must be positive.");
        }
    }
}
