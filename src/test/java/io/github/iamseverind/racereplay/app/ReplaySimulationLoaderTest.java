package io.github.iamseverind.racereplay.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.iamseverind.racereplay.core.ReplaySnapshot;
import io.github.iamseverind.racereplay.core.SyntheticReplaySimulation;
import io.github.iamseverind.racereplay.processed.ProcessedReplaySimulation;
import io.github.iamseverind.racereplay.processed.ReplayDriverState;
import io.github.iamseverind.racereplay.processed.ReplayTimelineFormat;
import io.github.iamseverind.racereplay.processed.ReplayTimelineHeader;
import io.github.iamseverind.racereplay.processed.ReplayTimelineWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests JavaFX replay-source selection.
 */
final class ReplaySimulationLoaderTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Uses synthetic data only when no processed cache exists.
     *
     * @throws Exception when closing the selected source fails
     */
    @Test
    void missingCacheUsesSyntheticFallback()
            throws Exception {

        final Path missingCache =
                temporaryDirectory.resolve(
                        "missing-cache");

        try (ReplaySimulationLoader.LoadedReplaySimulation loaded =
                ReplaySimulationLoader.load(
                        missingCache)) {

            assertFalse(
                    loaded.processed());

            assertEquals(
                    ReplaySimulationLoader
                            .SYNTHETIC_SUBTITLE,
                    loaded.subtitle());

            assertEquals(
                    missingCache,
                    loaded.cacheDirectory());

            assertEquals(
                    ReplaySimulationLoader
                            .SYNTHETIC_INITIAL_REPLAY_SECONDS,
                    loaded.initialReplaySeconds());

            assertEquals(
                    0.0,
                    loaded.raceStartSeconds());

            assertEquals(
                    ReplaySimulationLoader
                            .SYNTHETIC_RACE_LAPS,
                    loaded.scheduledLaps());

            assertTrue(
                    loaded.drsAvailable());

            assertInstanceOf(
                    SyntheticReplaySimulation.class,
                    loaded.simulation());

            assertEquals(
                    20,
                    loaded.simulation()
                            .snapshotAt(0.0)
                            .drivers()
                            .size());
        }
    }

    /**
     * Loads a complete processed timeline and closes its reader.
     *
     * @throws Exception when fixture creation or access fails
     */
    @Test
    void completeCacheUsesProcessedSimulation()
            throws Exception {

        final Path cacheDirectory =
                createProcessedFixture();

        final ProcessedReplaySimulation processed;

        try (ReplaySimulationLoader.LoadedReplaySimulation loaded =
                ReplaySimulationLoader.load(
                        cacheDirectory)) {

            assertTrue(
                    loaded.processed());

            assertEquals(
                    "2026 · BELGIUM · RACE · "
                    + "SPA-FRANCORCHAMPS · OPENF1",
                    loaded.subtitle());

            assertEquals(
                    cacheDirectory,
                    loaded.cacheDirectory());

            assertEquals(
                    0.0,
                    loaded.initialReplaySeconds());

            assertEquals(
                    231.934,
                    loaded.raceStartSeconds());

            assertEquals(
                    44,
                    loaded.scheduledLaps());

            assertFalse(
                    loaded.drsAvailable());

            assertEquals(
                    "#F47600",
                    loaded.teamColors()
                            .get("Apex Dynamics"));

            processed =
                    assertInstanceOf(
                            ProcessedReplaySimulation.class,
                            loaded.simulation());

            final ReplaySnapshot snapshot =
                    loaded.simulation()
                            .snapshotAt(0.0);

            assertEquals(
                    1,
                    snapshot.drivers().size());

            assertEquals(
                    "A01",
                    snapshot.drivers()
                            .getFirst()
                            .code());

            assertEquals(
                    "Apex Dynamics",
                    snapshot.drivers()
                            .getFirst()
                            .team());
        }

        assertThrows(
                IllegalStateException.class,
                () -> processed.snapshotAt(0.0));
    }

    /**
     * Rejects a partially existing processed cache.
     *
     * @throws Exception when fixture creation fails
     */
    @Test
    void partialCacheIsRejected()
            throws Exception {

        final Path cacheDirectory =
                temporaryDirectory.resolve(
                        "partial-cache");

        final Path processedDirectory =
                cacheDirectory.resolve(
                        "processed");

        Files.createDirectories(
                processedDirectory);

        Files.writeString(
                processedDirectory.resolve(
                        "drivers.json"),
                "[]",
                StandardCharsets.UTF_8);

        Files.writeString(
                processedDirectory.resolve(
                        "replay-manifest.json"),
                """
                {
                  "session": {
                    "year": 2026,
                    "country_name": "Belgium",
                    "session_name": "Race",
                    "circuit_short_name": "Spa-Francorchamps"
                  },
                  "replay": {
                    "start": "2026-07-19T13:00:00.139Z",
                    "scheduled_laps": 44
                  },
                  "timeline": {
                    "race_start": "2026-07-19T13:03:52.073Z"
                  }
                }
                """,
                StandardCharsets.UTF_8);

        assertThrows(
                IOException.class,
                () -> ReplaySimulationLoader.load(
                        cacheDirectory));
    }

    private Path createProcessedFixture()
            throws Exception {

        final Path cacheDirectory =
                temporaryDirectory.resolve(
                        "processed-cache");

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
                    "team_name":"Apex Dynamics",
                    "team_colour":"F47600"
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        Files.writeString(
                processedDirectory.resolve(
                        "replay-manifest.json"),
                """
                {
                  "session": {
                    "year": 2026,
                    "country_name": "Belgium",
                    "session_name": "Race",
                    "circuit_short_name": "Spa-Francorchamps"
                  },
                  "replay": {
                    "start": "2026-07-19T13:00:00.139Z",
                    "scheduled_laps": 44
                  },
                  "timeline": {
                    "race_start": "2026-07-19T13:03:52.073Z"
                  }
                }
                """,
                StandardCharsets.UTF_8);

        final ReplayTimelineHeader header =
                new ReplayTimelineHeader(
                        ReplayTimelineFormat.VERSION,
                        Instant.parse(
                                "2024-07-28T13:00:00Z"),
                        250,
                        2,
                        List.of(1));

        final ReplayDriverState state =
                new ReplayDriverState(
                        100,
                        200,
                        300,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        1,
                        ReplayTimelineFormat
                                .FLAG_LOCATION_VALID
                                | ReplayTimelineFormat
                                        .FLAG_TELEMETRY_VALID
                                | ReplayTimelineFormat
                                        .FLAG_POSITION_VALID
                                | ReplayTimelineFormat
                                        .FLAG_ACTIVE,
                        0,
                        Float.NaN,
                        Float.NaN,
                        0);

        try (ReplayTimelineWriter writer =
                new ReplayTimelineWriter(
                        processedDirectory.resolve(
                                "timeline.bin"),
                        header)) {

            writer.writeState(
                    0,
                    0,
                    state);

            writer.writeState(
                    1,
                    0,
                    state);

            writer.complete();
        }

        return cacheDirectory;
    }
}
