package io.github.iamseverind.racereplay.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates deterministic synthetic replay data for the desktop demo.
 */
public final class SyntheticReplaySimulation
        implements ReplaySimulation {

    private static final double LAP_DURATION_SECONDS = 92.0;
    private static final double TWO_PI = Math.PI * 2.0;

    private static final List<DriverSeed> DRIVER_SEEDS = List.of(
            new DriverSeed("A01", "Apex Dynamics", "MEDIUM"),
            new DriverSeed("A02", "Apex Dynamics", "HARD"),
            new DriverSeed("V01", "Vector Motorsport", "MEDIUM"),
            new DriverSeed("V02", "Vector Motorsport", "SOFT"),
            new DriverSeed("N01", "Northstar Racing", "MEDIUM"),
            new DriverSeed("N02", "Northstar Racing", "HARD"),
            new DriverSeed("H01", "Helix Competition", "MEDIUM"),
            new DriverSeed("H02", "Helix Competition", "SOFT"),
            new DriverSeed("E01", "Emerald Works", "HARD"),
            new DriverSeed("E02", "Emerald Works", "MEDIUM"),
            new DriverSeed("S01", "Summit Motors", "SOFT"),
            new DriverSeed("S02", "Summit Motors", "HARD"),
            new DriverSeed("O01", "Orbit Racing", "MEDIUM"),
            new DriverSeed("O02", "Orbit Racing", "SOFT"),
            new DriverSeed("C01", "Cobalt Engineering", "MEDIUM"),
            new DriverSeed("C02", "Cobalt Engineering", "HARD"),
            new DriverSeed("L01", "Lumen Autosport", "SOFT"),
            new DriverSeed("L02", "Lumen Autosport", "MEDIUM"),
            new DriverSeed("T01", "Titan Motorsport", "HARD"),
            new DriverSeed("T02", "Titan Motorsport", "MEDIUM"));

    /**
     * Creates one deterministic snapshot.
     *
     * @param replaySeconds non-negative logical replay time
     * @return synthetic replay snapshot
     */
    @Override
    public ReplaySnapshot snapshotAt(final double replaySeconds) {
        validateReplaySeconds(replaySeconds);

        final List<UnrankedDriver> unrankedDrivers =
                new ArrayList<>(DRIVER_SEEDS.size());

        for (int index = 0; index < DRIVER_SEEDS.size(); index++) {
            final DriverSeed seed = DRIVER_SEEDS.get(index);

            final double baseDistance =
                    replaySeconds / LAP_DURATION_SECONDS
                    - index * 0.0065;

            final double movementVariation =
                    Math.sin(replaySeconds * 0.026 + index * 0.71)
                    * 0.0012;

            final double totalDistance =
                    baseDistance + movementVariation;

            final double lapProgress = normalize(totalDistance);

            final double speedKph = clamp(
                    224.0
                    + 76.0 * Math.sin(lapProgress * TWO_PI)
                    + 28.0 * Math.sin(
                            lapProgress * TWO_PI * 3.0
                            + index * 0.19),
                    78.0,
                    344.0);

            final int gear = clamp(
                    (int) Math.round(speedKph / 43.0),
                    1,
                    8);

            final boolean drs =
                    lapProgress > 0.61
                    && lapProgress < 0.75
                    && speedKph > 235.0;

            unrankedDrivers.add(
                    new UnrankedDriver(
                            seed,
                            totalDistance,
                            lapProgress,
                            speedKph,
                            gear,
                            drs));
        }

        unrankedDrivers.sort(
                Comparator.comparingDouble(
                        UnrankedDriver::totalDistanceLaps)
                        .reversed());

        final List<DriverSnapshot> rankedDrivers =
                new ArrayList<>(unrankedDrivers.size());

        for (int index = 0; index < unrankedDrivers.size(); index++) {
            final UnrankedDriver driver = unrankedDrivers.get(index);

            rankedDrivers.add(
                    new DriverSnapshot(
                            index + 1,
                            driver.seed().code(),
                            driver.seed().team(),
                            driver.seed().tyre(),
                            driver.lapProgress(),
                            driver.totalDistanceLaps(),
                            driver.speedKph(),
                            driver.gear(),
                            driver.drs()));
        }

        return new ReplaySnapshot(replaySeconds, rankedDrivers);
    }

    private static double normalize(final double value) {
        final double normalized = value - Math.floor(value);

        if (normalized >= 1.0) {
            return 0.0;
        }

        return normalized;
    }

    private static double clamp(
            final double value,
            final double minimum,
            final double maximum) {

        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(
            final int value,
            final int minimum,
            final int maximum) {

        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void validateReplaySeconds(
            final double replaySeconds) {

        if (!Double.isFinite(replaySeconds)
                || replaySeconds < 0.0) {
            throw new IllegalArgumentException(
                    "Replay time must be non-negative and finite.");
        }
    }

    private record DriverSeed(
            String code,
            String team,
            String tyre) {
    }

    private record UnrankedDriver(
            DriverSeed seed,
            double totalDistanceLaps,
            double lapProgress,
            double speedKph,
            int gear,
            boolean drs) {
    }
}
