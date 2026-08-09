package io.github.iamseverind.racereplay.processed;

/**
 * Metadata fields that can be patched into one timeline state.
 *
 * @param position current race position
 * @param positionValid whether the position is valid
 * @param lapNumber current lap number
 * @param lapValid whether the lap number is valid
 * @param gapToLeaderSeconds gap to the leader
 * @param gapValid whether the gap is valid
 * @param intervalSeconds interval to the preceding driver
 * @param intervalValid whether the interval is valid
 * @param tyreCompoundCode normalized tyre compound code
 * @param tyreValid whether the tyre compound is valid
 */
public record ReplayTimelineMetadata(
        int position,
        boolean positionValid,
        int lapNumber,
        boolean lapValid,
        float gapToLeaderSeconds,
        boolean gapValid,
        float intervalSeconds,
        boolean intervalValid,
        int tyreCompoundCode,
        boolean tyreValid) {

    /**
     * Validates deterministic binary metadata values.
     */
    public ReplayTimelineMetadata {
        validatePosition(
                position,
                positionValid);

        validateLap(
                lapNumber,
                lapValid);

        validateSeconds(
                gapToLeaderSeconds,
                gapValid,
                "gapToLeaderSeconds");

        validateSeconds(
                intervalSeconds,
                intervalValid,
                "intervalSeconds");

        validateTyre(
                tyreCompoundCode,
                tyreValid);
    }

    /**
     * Creates metadata without any valid values.
     *
     * @return empty timeline metadata
     */
    public static ReplayTimelineMetadata empty() {
        return new ReplayTimelineMetadata(
                0,
                false,
                0,
                false,
                Float.NaN,
                false,
                Float.NaN,
                false,
                0,
                false);
    }

    /**
     * Returns the metadata validity flags.
     *
     * @return binary flags
     */
    public int validityFlags() {
        int flags = 0;

        if (positionValid) {
            flags |=
                    ReplayTimelineFormat
                            .FLAG_POSITION_VALID;
        }

        if (lapValid) {
            flags |=
                    ReplayTimelineFormat
                            .FLAG_LAP_VALID;
        }

        if (gapValid) {
            flags |=
                    ReplayTimelineFormat
                            .FLAG_GAP_VALID;
        }

        if (intervalValid) {
            flags |=
                    ReplayTimelineFormat
                            .FLAG_INTERVAL_VALID;
        }

        if (tyreValid) {
            flags |=
                    ReplayTimelineFormat
                            .FLAG_TYRE_VALID;
        }

        return flags;
    }

    private static void validatePosition(
            final int value,
            final boolean valid) {

        if (valid) {
            if (value <= 0 || value > 255) {
                throw new IllegalArgumentException(
                        "Valid position must be between 1 and 255.");
            }
        } else if (value != 0) {
            throw new IllegalArgumentException(
                    "Invalid position must be zero.");
        }
    }

    private static void validateLap(
            final int value,
            final boolean valid) {

        if (valid) {
            if (value <= 0 || value > 65_535) {
                throw new IllegalArgumentException(
                        "Valid lap must be between 1 and 65535.");
            }
        } else if (value != 0) {
            throw new IllegalArgumentException(
                    "Invalid lap must be zero.");
        }
    }

    private static void validateSeconds(
            final float value,
            final boolean valid,
            final String name) {

        if (valid) {
            if (!Float.isFinite(value) || value < 0.0F) {
                throw new IllegalArgumentException(
                        "Valid "
                        + name
                        + " must be finite and non-negative.");
            }
        } else if (!Float.isNaN(value)) {
            throw new IllegalArgumentException(
                    "Invalid "
                    + name
                    + " must be NaN.");
        }
    }

    private static void validateTyre(
            final int value,
            final boolean valid) {

        if (valid) {
            if (value <= 0 || value > 255) {
                throw new IllegalArgumentException(
                        "Valid tyre code must be between 1 and 255.");
            }
        } else if (value != 0) {
            throw new IllegalArgumentException(
                    "Invalid tyre code must be zero.");
        }
    }
}
