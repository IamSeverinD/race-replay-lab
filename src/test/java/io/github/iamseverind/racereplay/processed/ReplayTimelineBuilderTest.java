package io.github.iamseverind.racereplay.processed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests real timeline interpolation and reuse.
 */
final class ReplayTimelineBuilderTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Interpolates grouped raw samples and reuses the result.
     *
     * @throws Exception when fixture processing fails
     */
    @Test
    void buildsAndReusesTimeline()
            throws Exception {

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final Path cacheDirectory =
                createFixture(objectMapper);

        final ReplayTimelineBuilder builder =
                new ReplayTimelineBuilder(
                        objectMapper,
                        Clock.fixed(
                                Instant.parse(
                                        "2026-07-19T06:30:00Z"),
                                ZoneOffset.UTC));

        final ReplayTimelineBuildResult firstResult =
                builder.build(
                        cacheDirectory,
                        message -> { });

        assertFalse(
                firstResult.reused());

        assertEquals(
                5,
                firstResult.frameCount());

        assertEquals(
                2,
                firstResult.driverCount());

        assertEquals(
                10,
                firstResult.totalStates());

        assertEquals(
                1,
                firstResult.sourceInvalidTelemetryRecords());

        assertEquals(
                ReplayTimelineFormat
                        .expectedFileSizeBytes(
                                5,
                                2),
                firstResult.bytes());

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        firstResult.timelineFile())) {

            final ReplayDriverState interpolated =
                    reader.readState(
                            2,
                            0);

            assertEquals(
                    50,
                    interpolated.x());

            assertEquals(
                    100,
                    interpolated.y());

            assertEquals(
                    150,
                    interpolated.speed());

            assertEquals(
                    11_000,
                    interpolated.rpm());

            assertEquals(
                    50,
                    interpolated.throttle());

            assertEquals(
                    1,
                    interpolated.gear());

            assertEquals(
                    100,
                    interpolated.brake());

            assertEquals(
                    1,
                    interpolated.drs());

            assertTrue(
                    (interpolated.flags()
                            & ReplayTimelineFormat
                                    .FLAG_LOCATION_VALID)
                            != 0);

            assertTrue(
                    (interpolated.flags()
                            & ReplayTimelineFormat
                                    .FLAG_TELEMETRY_VALID)
                            != 0);

            assertTrue(
                    (interpolated.flags()
                            & ReplayTimelineFormat
                                    .FLAG_ACTIVE)
                            != 0);

            final ReplayDriverState unavailableDrs =
                    reader.readState(
                            0,
                            1);

            assertEquals(
                    0,
                    unavailableDrs.drs());

            assertTrue(
                    (unavailableDrs.flags()
                            & ReplayTimelineFormat
                                    .FLAG_TELEMETRY_VALID)
                            != 0);
        }

        final JsonNode manifest =
                objectMapper.readTree(
                        cacheDirectory
                                .resolve("processed")
                                .resolve(
                                        "replay-manifest.json")
                                .toFile());

        assertEquals(
                "timeline_complete",
                manifest.path("cache_state")
                        .asText());

        assertEquals(
                "complete",
                manifest.path("timeline")
                        .path("state")
                        .asText());

        assertEquals(
                firstResult.sha256(),
                manifest.path("timeline")
                        .path("sha256")
                        .asText());

        assertEquals(
                1,
                manifest.path("timeline")
                        .path(
                                "source_invalid_car_data_records")
                        .asLong());

        assertEquals(
                ReplayTimelineBuilder
                        .TELEMETRY_NORMALIZATION_VERSION,
                manifest.path("timeline")
                        .path(
                                "telemetry_normalization_version")
                        .asInt());

        final ReplayTimelineBuildResult secondResult =
                builder.build(
                        cacheDirectory,
                        message -> { });

        assertTrue(
                secondResult.reused());

        assertEquals(
                firstResult.sha256(),
                secondResult.sha256());

        try (var paths =
                Files.walk(
                        cacheDirectory
                                .resolve("processed"))) {

            assertFalse(
                    paths.anyMatch(path ->
                            path.getFileName()
                                    .toString()
                                    .contains(".part-")));
        }
    }

    private Path createFixture(
            final ObjectMapper objectMapper)
            throws Exception {

        final Path cacheDirectory =
                temporaryDirectory.resolve(
                        "2024-testland-race-9574");

        final Path rawDirectory =
                cacheDirectory.resolve("raw");

        final Path processedDirectory =
                cacheDirectory.resolve("processed");

        Files.createDirectories(rawDirectory);
        Files.createDirectories(processedDirectory);

        Files.writeString(
                processedDirectory.resolve(
                        "drivers.json"),
                """
                [
                  {"driver_number":1},
                  {"driver_number":44}
                ]
                """,
                StandardCharsets.UTF_8);

        Files.writeString(
                rawDirectory.resolve(
                        "location.json"),
                """
                [
                  {
                    "date":"2024-07-28T13:00:00Z",
                    "driver_number":1,
                    "x":0,
                    "y":0,
                    "z":0
                  },
                  {
                    "date":"2024-07-28T13:00:01Z",
                    "driver_number":1,
                    "x":100,
                    "y":200,
                    "z":300
                  },
                  {
                    "date":"2024-07-28T13:00:00Z",
                    "driver_number":44,
                    "x":200,
                    "y":300,
                    "z":400
                  },
                  {
                    "date":"2024-07-28T13:00:01Z",
                    "driver_number":44,
                    "x":300,
                    "y":400,
                    "z":500
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        Files.writeString(
                rawDirectory.resolve(
                        "car-data.json"),
                """
                [
                  {
                    "date":"2024-07-28T13:00:00Z",
                    "driver_number":1,
                    "speed":100,
                    "rpm":10000,
                    "n_gear":1,
                    "throttle":0,
                    "brake":100,
                    "drs":1
                  },
                  {
                    "date":"2024-07-28T13:00:00.500Z",
                    "driver_number":1,
                    "speed":150,
                    "rpm":11000,
                    "n_gear":2,
                    "throttle":104,
                    "brake":104,
                    "drs":1
                  },
                  {
                    "date":"2024-07-28T13:00:01Z",
                    "driver_number":1,
                    "speed":200,
                    "rpm":12000,
                    "n_gear":3,
                    "throttle":100,
                    "brake":0,
                    "drs":12
                  },
                  {
                    "date":"2024-07-28T13:00:00Z",
                    "driver_number":44,
                    "speed":150,
                    "rpm":11000,
                    "n_gear":2,
                    "throttle":20,
                    "brake":0,
                    "drs":null
                  },
                  {
                    "date":"2024-07-28T13:00:01Z",
                    "driver_number":44,
                    "speed":250,
                    "rpm":13000,
                    "n_gear":4,
                    "throttle":80,
                    "brake":100,
                    "drs":12
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        final ObjectNode manifest =
                objectMapper.createObjectNode();

        manifest.put(
                "cache_state",
                "metadata_complete");

        final ObjectNode replay =
                manifest.putObject("replay");

        replay.put(
                "start",
                "2024-07-28T13:00:00Z");

        replay.put(
                "end",
                "2024-07-28T13:00:01Z");

        replay.put(
                "frame_interval_millis",
                250);

        replay.put(
                "frame_count",
                5);

        replay.put(
                "driver_count",
                2);

        final ObjectNode ranges =
                manifest.putObject("source_ranges");

        ranges.putObject("location")
                .put("records", 4);

        ranges.putObject("car_data")
                .put("records", 5);

        final ObjectNode timeline =
                manifest.putObject("timeline");

        timeline.put(
                "state",
                "pending");

        timeline.put(
                "path",
                "timeline.bin");

        timeline.put(
                "expected_bytes",
                ReplayTimelineFormat
                        .expectedFileSizeBytes(
                                5,
                                2));

        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(
                        processedDirectory
                                .resolve(
                                        "replay-manifest.json")
                                .toFile(),
                        manifest);

        return cacheDirectory;
    }
}
