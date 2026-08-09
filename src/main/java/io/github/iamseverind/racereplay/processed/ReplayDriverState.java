package io.github.iamseverind.racereplay.processed;

/**
 * Fixed-size driver state stored in a timeline frame.
 *
 * @param x OpenF1 X coordinate
 * @param y OpenF1 Y coordinate
 * @param z OpenF1 Z coordinate
 * @param speed speed in kilometres per hour
 * @param rpm engine revolutions per minute
 * @param gear current gear
 * @param throttle throttle percentage
 * @param brake brake percentage
 * @param drs OpenF1 DRS value
 * @param position race position
 * @param flags validity and activity bit mask
 * @param lapNumber current lap
 * @param gapToLeaderSeconds gap to leader
 * @param intervalSeconds interval to preceding driver
 * @param tyreCompoundCode normalized tyre compound code
 */
public record ReplayDriverState(
        int x,
        int y,
        int z,
        int speed,
        int rpm,
        int gear,
        int throttle,
        int brake,
        int drs,
        int position,
        int flags,
        int lapNumber,
        float gapToLeaderSeconds,
        float intervalSeconds,
        int tyreCompoundCode) {

    /**
     * Validates fixed-width binary fields.
     */
    public ReplayDriverState {
        requireUnsignedShort(
                speed,
                "speed");

        requireUnsignedShort(
                rpm,
                "rpm");

        requireUnsignedByte(
                gear,
                "gear");

        requireUnsignedByte(
                throttle,
                "throttle");

        requireUnsignedByte(
                brake,
                "brake");

        requireUnsignedByte(
                drs,
                "drs");

        requireUnsignedByte(
                position,
                "position");

        requireUnsignedByte(
                flags,
                "flags");

        requireUnsignedShort(
                lapNumber,
                "lapNumber");

        requireUnsignedByte(
                tyreCompoundCode,
                "tyreCompoundCode");

        requireFiniteOrNaN(
                gapToLeaderSeconds,
                "gapToLeaderSeconds");

        requireFiniteOrNaN(
                intervalSeconds,
                "intervalSeconds");
    }

    /**
     * Creates an entirely invalid placeholder state.
     *
     * @return empty driver state
     */
    public static ReplayDriverState empty() {
        return new ReplayDriverState(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                Float.NaN,
                Float.NaN,
                0);
    }

    private static void requireUnsignedByte(
            final int value,
            final String name) {

        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(
                    name
                    + " must be between 0 and 255.");
        }
    }

    private static void requireUnsignedShort(
            final int value,
            final String name) {

        if (value < 0 || value > 65_535) {
            throw new IllegalArgumentException(
                    name
                    + " must be between 0 and 65535.");
        }
    }

    private static void requireFiniteOrNaN(
            final float value,
            final String name) {

        if (!Float.isNaN(value)
                && !Float.isFinite(value)) {

            throw new IllegalArgumentException(
                    name
                    + " must be finite or NaN.");
        }
    }
}
