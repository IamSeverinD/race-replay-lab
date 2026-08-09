package io.github.iamseverind.racereplay.core;

import java.util.Objects;

/**
 * Immutable state of one driver at one replay point.
 *
 * @param position race position
 * @param code three-letter driver code
 * @param team team name
 * @param tyre tyre compound
 * @param tyreValid whether tyre information is available
 * @param lapProgress normalized progress from zero to below one
 * @param totalDistanceLaps total travelled race distance in laps
 * @param speedKph speed in kilometres per hour
 * @param rpm engine revolutions per minute
 * @param gear current gear from zero to eight
 * @param throttle throttle percentage
 * @param brake brake percentage
 * @param drs whether DRS is active
 * @param telemetryValid whether telemetry values are available
 * @param lapNumber current lap number
 * @param lapValid whether lap information is available
 * @param gapToLeaderSeconds gap to the leader
 * @param gapValid whether the gap is available
 * @param intervalSeconds interval to the preceding driver
 * @param intervalValid whether the interval is available
 * @param locationX OpenF1 X coordinate
 * @param locationY OpenF1 Y coordinate
 * @param locationZ OpenF1 Z coordinate
 * @param locationValid whether OpenF1 coordinates are available
 */
public record DriverSnapshot(
        int position,
        String code,
        String team,
        String tyre,
        boolean tyreValid,
        double lapProgress,
        double totalDistanceLaps,
        double speedKph,
        int rpm,
        int gear,
        int throttle,
        int brake,
        boolean drs,
        boolean telemetryValid,
        int lapNumber,
        boolean lapValid,
        double gapToLeaderSeconds,
        boolean gapValid,
        double intervalSeconds,
        boolean intervalValid,
        int locationX,
        int locationY,
        int locationZ,
        boolean locationValid) {

    /**
     * Validates one immutable driver state.
     */
    public DriverSnapshot {
        if (position <= 0) {
            throw new IllegalArgumentException(
                    "Position must be positive.");
        }

        code = Objects.requireNonNull(code, "code");
        team = Objects.requireNonNull(team, "team");
        tyre = Objects.requireNonNull(tyre, "tyre");

        if (!Double.isFinite(lapProgress)
                || lapProgress < 0.0
                || lapProgress >= 1.0) {

            throw new IllegalArgumentException(
                    "Lap progress must be between zero and one.");
        }

        if (!Double.isFinite(totalDistanceLaps)) {
            throw new IllegalArgumentException(
                    "Total distance must be finite.");
        }

        if (!Double.isFinite(speedKph)
                || speedKph < 0.0) {

            throw new IllegalArgumentException(
                    "Speed must be non-negative and finite.");
        }

        if (rpm < 0 || rpm > 65_535) {
            throw new IllegalArgumentException(
                    "RPM must be between zero and 65535.");
        }

        if (gear < 0 || gear > 8) {
            throw new IllegalArgumentException(
                    "Gear must be between zero and eight.");
        }

        requirePercentage(
                throttle,
                "Throttle");

        requirePercentage(
                brake,
                "Brake");

        if (lapValid && lapNumber <= 0) {
            throw new IllegalArgumentException(
                    "A valid lap number must be positive.");
        }

        if (!lapValid && lapNumber != 0) {
            throw new IllegalArgumentException(
                    "An unavailable lap number must be zero.");
        }

        requireOptionalSeconds(
                gapToLeaderSeconds,
                gapValid,
                "Gap");

        requireOptionalSeconds(
                intervalSeconds,
                intervalValid,
                "Interval");
    }

    /**
     * Compatibility constructor including optional coordinates.
     *
     * @param position race position
     * @param code driver code
     * @param team team name
     * @param tyre tyre compound
     * @param lapProgress normalized lap progress
     * @param totalDistanceLaps travelled distance
     * @param speedKph speed
     * @param gear gear
     * @param drs DRS state
     * @param locationX X coordinate
     * @param locationY Y coordinate
     * @param locationZ Z coordinate
     * @param locationValid coordinate validity
     */
    public DriverSnapshot(
            final int position,
            final String code,
            final String team,
            final String tyre,
            final double lapProgress,
            final double totalDistanceLaps,
            final double speedKph,
            final int gear,
            final boolean drs,
            final int locationX,
            final int locationY,
            final int locationZ,
            final boolean locationValid) {

        this(
                position,
                code,
                team,
                tyre,
                !"UNKNOWN".equalsIgnoreCase(tyre),
                lapProgress,
                totalDistanceLaps,
                speedKph,
                inferredRpm(gear),
                gear,
                100,
                0,
                drs,
                true,
                inferredLapNumber(totalDistanceLaps),
                true,
                Double.NaN,
                false,
                Double.NaN,
                false,
                locationX,
                locationY,
                locationZ,
                locationValid);
    }

    /**
     * Compatibility constructor without real coordinates.
     *
     * @param position race position
     * @param code driver code
     * @param team team name
     * @param tyre tyre compound
     * @param lapProgress normalized lap progress
     * @param totalDistanceLaps travelled distance
     * @param speedKph speed
     * @param gear gear
     * @param drs DRS state
     */
    public DriverSnapshot(
            final int position,
            final String code,
            final String team,
            final String tyre,
            final double lapProgress,
            final double totalDistanceLaps,
            final double speedKph,
            final int gear,
            final boolean drs) {

        this(
                position,
                code,
                team,
                tyre,
                lapProgress,
                totalDistanceLaps,
                speedKph,
                gear,
                drs,
                0,
                0,
                0,
                false);
    }

    private static int inferredLapNumber(
            final double totalDistanceLaps) {

        return Math.max(
                1,
                (int) Math.floor(totalDistanceLaps) + 1);
    }

    private static int inferredRpm(
            final int gear) {

        return Math.max(
                0,
                Math.min(
                        15_000,
                        gear * 1_700));
    }

    private static void requirePercentage(
            final int value,
            final String name) {

        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(
                    name + " must be between zero and 100.");
        }
    }

    private static void requireOptionalSeconds(
            final double value,
            final boolean valid,
            final String name) {

        if (valid) {
            if (!Double.isFinite(value)
                    || value < 0.0) {

                throw new IllegalArgumentException(
                        name
                        + " must be non-negative and finite "
                        + "when valid.");
            }

            return;
        }

        if (!Double.isNaN(value)) {
            throw new IllegalArgumentException(
                    name + " must be NaN when unavailable.");
        }
    }
}
