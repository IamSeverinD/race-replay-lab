package io.github.iamseverind.racereplay.processed;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests complete replay timeline enrichment.
 */
final class ReplayTimelineEnricherTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Enriches position, lap, intervals and tyre metadata.
     *
     * @throws Exception when fixture processing fails
     */
    @Test
    void enrichesAndReusesTimeline()
            throws Exception {

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final Path cacheDirectory =
                createFixture(
                        objectMapper);

        final ReplayTimelineEnricher enricher =
                new ReplayTimelineEnricher(
                        objectMapper,
                        Clock.fixed(
                                Instant.parse(
                                        "2026-07-19T10:00:00Z"),
                                ZoneOffset.UTC));

        final ReplayTimelineEnrichmentResult first =
                enricher.enrich(
                        cacheDirectory,
                        message -> { });

        assertFalse(
                first.reused());

        assertEquals(
                10,
                first.totalStates());

        assertEquals(
                10,
                first.positionValidStates());

        assertEquals(
                8,
                first.lapValidStates());

        assertEquals(
                6,
                first.gapValidStates());

        assertEquals(
                6,
                first.intervalValidStates());

        assertEquals(
                6,
                first.tyreValidStates());

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        first.timelineFile())) {

            final ReplayDriverState preRace =
                    reader.readState(
                            0,
                            0);

            assertEquals(
                    2,
                    preRace.position());

            assertEquals(
                    0,
                    preRace.lapNumber());

            assertEquals(
                    0,
                    preRace.tyreCompoundCode());

            assertTrue(
                    (preRace.flags()
                            & ReplayTimelineFormat
                                    .FLAG_POSITION_VALID)
                            != 0);

            assertFalse(
                    (preRace.flags()
                            & ReplayTimelineFormat
                                    .FLAG_LAP_VALID)
                            != 0);

            final ReplayDriverState lapOne =
                    reader.readState(
                            2,
                            0);

            assertEquals(
                    1,
                    lapOne.position());

            assertEquals(
                    1,
                    lapOne.lapNumber());

            assertEquals(
                    ReplayTimelineEnricher.TYRE_SOFT,
                    lapOne.tyreCompoundCode());

            assertEquals(
                    1.5F,
                    lapOne.gapToLeaderSeconds());

            assertEquals(
                    0.5F,
                    lapOne.intervalSeconds());

            assertTrue(
                    (lapOne.flags()
                            & ReplayTimelineFormat
                                    .FLAG_LOCATION_VALID)
                            != 0);

            assertTrue(
                    (lapOne.flags()
                            & ReplayTimelineFormat
                                    .FLAG_TELEMETRY_VALID)
                            != 0);

            final ReplayDriverState lapTwo =
                    reader.readState(
                            3,
                            0);

            assertEquals(
                    2,
                    lapTwo.lapNumber());

            assertEquals(
                    ReplayTimelineEnricher.TYRE_MEDIUM,
                    lapTwo.tyreCompoundCode());

            assertTrue(
                    Float.isNaN(
                            lapTwo.gapToLeaderSeconds()));

            assertTrue(
                    Float.isNaN(
                            lapTwo.intervalSeconds()));

            assertFalse(
                    (lapTwo.flags()
                            & ReplayTimelineFormat
                                    .FLAG_GAP_VALID)
                            != 0);

            assertFalse(
                    (lapTwo.flags()
                            & ReplayTimelineFormat
                                    .FLAG_INTERVAL_VALID)
                            != 0);

            final ReplayDriverState uncoveredTyre =
                    reader.readState(
                            3,
                            1);

            assertEquals(
                    2,
                    uncoveredTyre.lapNumber());

            assertEquals(
                    0,
                    uncoveredTyre.tyreCompoundCode());

            assertTrue(
                    (uncoveredTyre.flags()
                            & ReplayTimelineFormat
                                    .FLAG_LAP_VALID)
                            != 0);

            assertFalse(
                    (uncoveredTyre.flags()
                            & ReplayTimelineFormat
                                    .FLAG_TYRE_VALID)
                            != 0);
        }

        final ReplayTimelineEnrichmentResult second =
                enricher.enrich(
                        cacheDirectory,
                        message -> { });

        assertTrue(
                second.reused());

        assertEquals(
                first.sha256(),
                second.sha256());
    }

    /**
     * Rejects duplicate grid positions without changing the timeline.
     *
     * @throws Exception when fixture access fails
     */
    @Test
    void duplicateGridPositionsPreserveTimeline()
            throws Exception {

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final Path cacheDirectory =
                createFixture(
                        objectMapper);

        final Path timelineFile =
                cacheDirectory.resolve("processed")
                        .resolve("timeline.bin");

        final byte[] original =
                Files.readAllBytes(
                        timelineFile);

        Files.writeString(
                cacheDirectory.resolve("raw")
                        .resolve("position.json"),
                """
                [
                  {
                    "date":"2024-07-28T12:59:59Z",
                    "driver_number":1,
                    "position":1
                  },
                  {
                    "date":"2024-07-28T12:59:59Z",
                    "driver_number":44,
                    "position":1
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        final ReplayTimelineEnricher enricher =
                new ReplayTimelineEnricher(
                        objectMapper,
                        Clock.systemUTC());

        assertThrows(
                java.io.IOException.class,
                () -> enricher.enrich(
                        cacheDirectory,
                        message -> { }));

        assertArrayEquals(
                original,
                Files.readAllBytes(
                        timelineFile));
    }

    /**
     * Enriches a session whose provider exposes no interval metadata.
     *
     * @throws Exception when fixture processing fails
     */
    @Test
    void enrichesTimelineWithoutIntervals()
            throws Exception {

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final Path cacheDirectory =
                createFixture(objectMapper);

        Files.writeString(
                cacheDirectory.resolve("raw")
                        .resolve("intervals.json"),
                "[]",
                StandardCharsets.UTF_8);

        final ReplayTimelineEnrichmentResult result =
                new ReplayTimelineEnricher(
                        objectMapper,
                        Clock.systemUTC())
                        .enrich(
                                cacheDirectory,
                                message -> { });

        assertEquals(
                0,
                result.gapValidStates());

        assertEquals(
                0,
                result.intervalValidStates());

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        result.timelineFile())) {

            final ReplayDriverState state =
                    reader.readState(
                            2,
                            0);

            assertTrue(
                    Float.isNaN(
                            state.gapToLeaderSeconds()));

            assertTrue(
                    Float.isNaN(
                            state.intervalSeconds()));

            assertFalse(
                    (state.flags()
                            & ReplayTimelineFormat
                                    .FLAG_GAP_VALID)
                            != 0);

            assertFalse(
                    (state.flags()
                            & ReplayTimelineFormat
                                    .FLAG_INTERVAL_VALID)
                            != 0);
        }
    }

    private Path createFixture(
            final ObjectMapper objectMapper)
            throws Exception {

        final Path cacheDirectory =
                temporaryDirectory.resolve(
                        "cache");

        final Path rawDirectory =
                cacheDirectory.resolve("raw");

        final Path processedDirectory =
                cacheDirectory.resolve("processed");

        Files.createDirectories(rawDirectory);
        Files.createDirectories(processedDirectory);

        final Path timelineFile =
                processedDirectory.resolve(
                        "timeline.bin");

        final ReplayTimelineHeader header =
                new ReplayTimelineHeader(
                        ReplayTimelineFormat.VERSION,
                        Instant.parse(
                                "2024-07-28T13:00:00Z"),
                        250,
                        5,
                        List.of(1, 44));

        final ReplayDriverState baseState =
                new ReplayDriverState(
                        100,
                        200,
                        300,
                        250,
                        12_000,
                        8,
                        100,
                        0,
                        12,
                        0,
                        ReplayTimelineFormat
                                .FLAG_LOCATION_VALID
                                | ReplayTimelineFormat
                                        .FLAG_TELEMETRY_VALID
                                | ReplayTimelineFormat
                                        .FLAG_ACTIVE,
                        0,
                        Float.NaN,
                        Float.NaN,
                        0);

        try (ReplayTimelineWriter writer =
                new ReplayTimelineWriter(
                        timelineFile,
                        header)) {

            for (int frame = 0;
                    frame < header.frameCount();
                    frame++) {

                for (int driver = 0;
                        driver < header.driverCount();
                        driver++) {

                    writer.writeState(
                            frame,
                            driver,
                            baseState);
                }
            }

            writer.complete();
        }

        final ObjectNode manifest =
                objectMapper.createObjectNode();

        manifest.put(
                "cache_state",
                "timeline_complete");

        final ObjectNode timeline =
                manifest.putObject("timeline");

        timeline.put(
                "state",
                "complete");

        timeline.put(
                "bytes",
                Files.size(timelineFile));

        timeline.put(
                "sha256",
                sha256(timelineFile));

        timeline.put(
                "total_states",
                10);

        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(
                        processedDirectory.resolve(
                                "replay-manifest.json")
                                .toFile(),
                        manifest);

        Files.writeString(
                rawDirectory.resolve(
                        "position.json"),
                """
                [
                  {
                    "date":"2024-07-28T12:59:59Z",
                    "driver_number":1,
                    "position":2
                  },
                  {
                    "date":"2024-07-28T12:59:59Z",
                    "driver_number":44,
                    "position":1
                  },
                  {
                    "date":"2024-07-28T13:00:00.500Z",
                    "driver_number":1,
                    "position":1
                  },
                  {
                    "date":"2024-07-28T13:00:00.500Z",
                    "driver_number":44,
                    "position":2
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        Files.writeString(
                rawDirectory.resolve(
                        "intervals.json"),
                """
                [
                  {
                    "date":"2024-07-28T13:00:00.250Z",
                    "driver_number":1,
                    "gap_to_leader":1.5,
                    "interval":0.5
                  },
                  {
                    "date":"2024-07-28T13:00:00.250Z",
                    "driver_number":44,
                    "gap_to_leader":0.0,
                    "interval":0.0
                  },
                  {
                    "date":"2024-07-28T13:00:00.750Z",
                    "driver_number":1,
                    "gap_to_leader":"+1 LAP",
                    "interval":null
                  },
                  {
                    "date":"2024-07-28T13:00:00.750Z",
                    "driver_number":44,
                    "gap_to_leader":2.0,
                    "interval":1.0
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        Files.writeString(
                rawDirectory.resolve(
                        "laps.json"),
                """
                [
                  {
                    "date_start":"2024-07-28T13:00:00.250Z",
                    "driver_number":1,
                    "lap_number":1
                  },
                  {
                    "date_start":"2024-07-28T13:00:00.250Z",
                    "driver_number":44,
                    "lap_number":1
                  },
                  {
                    "date_start":"2024-07-28T13:00:00.750Z",
                    "driver_number":1,
                    "lap_number":2
                  },
                  {
                    "date_start":"2024-07-28T13:00:00.750Z",
                    "driver_number":44,
                    "lap_number":2
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        Files.writeString(
                rawDirectory.resolve(
                        "stints.json"),
                """
                [
                  {
                    "driver_number":1,
                    "stint_number":1,
                    "lap_start":1,
                    "lap_end":1,
                    "compound":"SOFT"
                  },
                  {
                    "driver_number":1,
                    "stint_number":2,
                    "lap_start":2,
                    "lap_end":2,
                    "compound":"MEDIUM"
                  },
                  {
                    "driver_number":44,
                    "stint_number":1,
                    "lap_start":1,
                    "lap_end":1,
                    "compound":"HARD"
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        return cacheDirectory;
    }

    private static String sha256(
            final Path file)
            throws Exception {

        final MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256");

        digest.update(
                Files.readAllBytes(file));

        return HexFormat.of()
                .formatHex(
                        digest.digest());
    }
}
