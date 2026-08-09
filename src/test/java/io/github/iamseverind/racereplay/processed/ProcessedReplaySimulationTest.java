package io.github.iamseverind.racereplay.processed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.iamseverind.racereplay.core.DriverSnapshot;
import io.github.iamseverind.racereplay.core.ReplaySnapshot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests conversion from processed timeline frames to UI snapshots.
 */
final class ProcessedReplaySimulationTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Maps metadata, frame timing and lap progress.
     *
     * @throws Exception when fixture access fails
     */
    @Test
    void createsReplaySnapshotsFromProcessedFrames()
            throws Exception {

        final Path cacheDirectory =
                createFixture();

        try (ProcessedReplaySimulation simulation =
                new ProcessedReplaySimulation(
                        cacheDirectory)) {

            assertEquals(
                    1.0,
                    simulation.durationSeconds());

            final ReplaySnapshot snapshot =
                    simulation.snapshotAt(
                            0.6);

            assertEquals(
                    0.6,
                    snapshot.replaySeconds());

            assertEquals(
                    2,
                    snapshot.drivers().size());

            final DriverSnapshot leader =
                    snapshot.drivers().get(0);

            final DriverSnapshot second =
                    snapshot.drivers().get(1);

            assertEquals(
                    "V01",
                    leader.code());

            assertEquals(
                    "Vector Motorsport",
                    leader.team());

            assertEquals(
                    1,
                    leader.position());

            assertEquals(
                    0,
                    leader.gear());

            assertFalse(
                    leader.drs());

            assertEquals(
                    "A01",
                    second.code());

            assertEquals(
                    "Apex Dynamics",
                    second.team());

            assertEquals(
                    2,
                    second.position());

            assertEquals(
                    "MEDIUM",
                    second.tyre());

            assertEquals(
                    0.7,
                    second.lapProgress());

            assertEquals(
                    0.7,
                    second.totalDistanceLaps());

            assertEquals(
                    281.0,
                    second.speedKph());

            assertEquals(
                    8,
                    second.gear());

            assertTrue(
                    second.drs());

            assertEquals(
                    12_000,
                    second.rpm());

            assertEquals(
                    100,
                    second.throttle());

            assertEquals(
                    0,
                    second.brake());

            assertTrue(
                    second.telemetryValid());

            assertEquals(
                    1,
                    second.lapNumber());

            assertTrue(
                    second.lapValid());

            assertTrue(
                    second.tyreValid());

            assertFalse(
                    second.gapValid());

            assertTrue(
                    Double.isNaN(
                            second.gapToLeaderSeconds()));

            assertFalse(
                    second.intervalValid());

            assertTrue(
                    Double.isNaN(
                            second.intervalSeconds()));

            assertTrue(
                    second.locationValid());

            assertEquals(
                    100,
                    second.locationX());

            assertEquals(
                    200,
                    second.locationY());

            assertEquals(
                    300,
                    second.locationZ());
        }
    }

    /**
     * Clamps the final frame and rejects access after closing.
     *
     * @throws Exception when fixture access fails
     */
    @Test
    void clampsReplayEndAndClosesReader()
            throws Exception {

        final Path cacheDirectory =
                createFixture();

        final ProcessedReplaySimulation simulation =
                new ProcessedReplaySimulation(
                        cacheDirectory);

        final ReplaySnapshot finalSnapshot =
                simulation.snapshotAt(
                        100.0);

        assertEquals(
                1.0,
                finalSnapshot.replaySeconds());

        final DriverSnapshot primaryDriver =
                finalSnapshot.drivers()
                        .stream()
                        .filter(driver ->
                                driver.code()
                                        .equals("A01"))
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                1.5,
                primaryDriver.totalDistanceLaps());

        simulation.close();
        simulation.close();

        assertThrows(
                IllegalStateException.class,
                () -> simulation.snapshotAt(0.0));

        try (ProcessedReplaySimulation negativeTimeSimulation =
                new ProcessedReplaySimulation(
                        cacheDirectory)) {

            assertThrows(
                    IllegalArgumentException.class,
                    () -> negativeTimeSimulation
                            .snapshotAt(-1.0));
        }
    }

    /**
     * Removes a driver after both telemetry and its short grace period end.
     *
     * @throws Exception when the retirement fixture cannot be created
     */
    @Test
    void hidesDriverAfterTelemetryEnds()
            throws Exception {

        final Path cacheDirectory =
                createRetirementFixture();

        try (ProcessedReplaySimulation simulation =
                new ProcessedReplaySimulation(
                        cacheDirectory)) {

            assertEquals(
                    1,
                    simulation.snapshotAt(1.0)
                            .drivers()
                            .size());

            assertTrue(
                    simulation.snapshotAt(7.25)
                            .drivers()
                            .isEmpty());
        }
    }

    private Path createFixture()
            throws Exception {

        final Path cacheDirectory =
                temporaryDirectory.resolve(
                        "cache");

        final Path processedDirectory =
                cacheDirectory.resolve(
                        "processed");

        Files.createDirectories(
                processedDirectory);

        Files.writeString(
                processedDirectory.resolve(
                        "drivers.json"),
                """
                [
                  {
                    "driver_number":1,
                    "name_acronym":"A01",
                    "team_name":"Apex Dynamics"
                  },
                  {
                    "driver_number":44,
                    "name_acronym":"V01",
                    "team_name":"Vector Motorsport"
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        final ReplayTimelineHeader header =
                new ReplayTimelineHeader(
                        ReplayTimelineFormat.VERSION,
                        Instant.parse(
                                "2024-07-28T13:00:00Z"),
                        250,
                        5,
                        List.of(1, 44));

        final Path timelineFile =
                processedDirectory.resolve(
                        "timeline.bin");

        try (ReplayTimelineWriter writer =
                new ReplayTimelineWriter(
                        timelineFile,
                        header)) {

            writer.writeState(
                    0,
                    0,
                    state(
                            2,
                            0,
                            0,
                            0,
                            0,
                            false,
                            false));

            writer.writeState(
                    0,
                    1,
                    state(
                            1,
                            0,
                            0,
                            0,
                            0,
                            false,
                            false));

            writer.writeState(
                    1,
                    0,
                    state(
                            2,
                            1,
                            2,
                            7,
                            280,
                            true,
                            true));

            writer.writeState(
                    1,
                    1,
                    state(
                            1,
                            1,
                            3,
                            6,
                            270,
                            true,
                            true));

            writer.writeState(
                    2,
                    0,
                    state(
                            2,
                            1,
                            2,
                            8,
                            308,
                            true,
                            true));

            writer.writeState(
                    2,
                    1,
                    state(
                            1,
                            1,
                            3,
                            0,
                            0,
                            false,
                            false));

            writer.writeState(
                    3,
                    0,
                    state(
                            1,
                            2,
                            1,
                            6,
                            240,
                            true,
                            true));

            writer.writeState(
                    3,
                    1,
                    state(
                            2,
                            1,
                            3,
                            5,
                            220,
                            true,
                            true));

            writer.writeState(
                    4,
                    0,
                    state(
                            1,
                            2,
                            1,
                            7,
                            290,
                            true,
                            true));

            writer.writeState(
                    4,
                    1,
                    state(
                            2,
                            1,
                            3,
                            5,
                            215,
                            true,
                            true));

            writer.complete();
        }

        return cacheDirectory;
    }

    private Path createRetirementFixture()
            throws Exception {

        final Path cacheDirectory =
                temporaryDirectory.resolve(
                        "retirement-cache");

        final Path processedDirectory =
                cacheDirectory.resolve(
                        "processed");

        Files.createDirectories(
                processedDirectory);

        Files.writeString(
                processedDirectory.resolve(
                        "drivers.json"),
                """
                [
                  {
                    "driver_number":63,
                    "name_acronym":"RUS",
                    "team_name":"Mercedes"
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        final int frameCount = 30;

        final ReplayTimelineHeader header =
                new ReplayTimelineHeader(
                        ReplayTimelineFormat.VERSION,
                        Instant.parse(
                                "2026-07-19T13:00:00Z"),
                        250,
                        frameCount,
                        List.of(63));

        try (ReplayTimelineWriter writer =
                new ReplayTimelineWriter(
                        processedDirectory.resolve(
                                "timeline.bin"),
                        header)) {

            for (int frameIndex = 0;
                    frameIndex < frameCount;
                    frameIndex++) {

                writer.writeState(
                        frameIndex,
                        0,
                        state(
                                1,
                                1,
                                3,
                                frameIndex < 2
                                        ? 6
                                        : 0,
                                frameIndex < 2
                                        ? 250
                                        : 0,
                                frameIndex < 2,
                                true));
            }

            writer.complete();
        }

        return cacheDirectory;
    }

    private static ReplayDriverState state(
            final int position,
            final int lapNumber,
            final int tyreCode,
            final int gear,
            final int speed,
            final boolean telemetryValid,
            final boolean tyreValid) {

        int flags =
                ReplayTimelineFormat
                        .FLAG_LOCATION_VALID
                | ReplayTimelineFormat
                        .FLAG_POSITION_VALID
                | ReplayTimelineFormat
                        .FLAG_ACTIVE;

        if (lapNumber > 0) {
            flags |=
                    ReplayTimelineFormat
                            .FLAG_LAP_VALID;
        }

        if (telemetryValid) {
            flags |=
                    ReplayTimelineFormat
                            .FLAG_TELEMETRY_VALID;
        }

        if (tyreValid) {
            flags |=
                    ReplayTimelineFormat
                            .FLAG_TYRE_VALID;
        }

        return new ReplayDriverState(
                100,
                200,
                300,
                speed,
                telemetryValid
                        ? 12_000
                        : 0,
                gear,
                telemetryValid
                        ? 100
                        : 0,
                0,
                telemetryValid
                        ? 12
                        : 0,
                position,
                flags,
                lapNumber,
                Float.NaN,
                Float.NaN,
                tyreCode);
    }
}
