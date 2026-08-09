package io.github.iamseverind.racereplay.processed;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Result of enriching a replay timeline with race metadata.
 *
 * @param timelineFile enriched timeline file
 * @param reused whether an existing enrichment was reused
 * @param bytes timeline size
 * @param sha256 timeline checksum
 * @param totalStates total driver states
 * @param positionValidStates states with a valid position
 * @param lapValidStates states with a valid lap
 * @param gapValidStates states with a valid leader gap
 * @param intervalValidStates states with a valid interval
 * @param tyreValidStates states with a valid tyre compound
 */
public record ReplayTimelineEnrichmentResult(
        Path timelineFile,
        boolean reused,
        long bytes,
        String sha256,
        long totalStates,
        long positionValidStates,
        long lapValidStates,
        long gapValidStates,
        long intervalValidStates,
        long tyreValidStates) {

    /**
     * Validates enrichment statistics.
     */
    public ReplayTimelineEnrichmentResult {
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

        if (bytes <= 0 || totalStates <= 0) {
            throw new IllegalArgumentException(
                    "Timeline totals must be positive.");
        }

        validateCount(
                positionValidStates,
                totalStates,
                "positionValidStates");

        validateCount(
                lapValidStates,
                totalStates,
                "lapValidStates");

        validateCount(
                gapValidStates,
                totalStates,
                "gapValidStates");

        validateCount(
                intervalValidStates,
                totalStates,
                "intervalValidStates");

        validateCount(
                tyreValidStates,
                totalStates,
                "tyreValidStates");
    }

    private static void validateCount(
            final long count,
            final long total,
            final String name) {

        if (count < 0 || count > total) {
            throw new IllegalArgumentException(
                    "Invalid "
                    + name
                    + ": "
                    + count);
        }
    }
}
