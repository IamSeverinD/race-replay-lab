package io.github.iamseverind.racereplay.processed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests processed replay metadata creation.
 */
final class ProcessedReplayCacheBuilderTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Builds normalized drivers and a deterministic replay manifest.
     *
     * @throws Exception when fixture files cannot be created
     */
    @Test
    void buildsProcessedReplayMetadata()
            throws Exception {

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final Path cacheDirectory =
                createRawCache(
                        objectMapper,
                        "raw_complete");

        final ProcessedReplayCacheBuilder builder =
                new ProcessedReplayCacheBuilder(
                        objectMapper,
                        Clock.fixed(
                                Instant.parse(
                                        "2026-07-19T06:00:00Z"),
                                ZoneOffset.UTC));

        final ProcessedReplayBuildResult result =
                builder.build(
                        cacheDirectory);

        assertEquals(
                Instant.parse(
                        "2024-07-28T13:00:00.500Z"),
                result.replayStart());

        assertEquals(
                Instant.parse(
                        "2024-07-28T13:00:01Z"),
                result.replayEnd());

        assertEquals(
                250,
                result.frameIntervalMillis());

        assertEquals(
                3,
                result.frameCount());

        assertEquals(
                2,
                result.driverCount());

        final JsonNode drivers =
                objectMapper.readTree(
                        result.driversFile().toFile());

        assertEquals(
                1,
                drivers.get(0)
                        .path("driver_number")
                        .asInt());

        assertEquals(
                44,
                drivers.get(1)
                        .path("driver_number")
                        .asInt());

        assertEquals(
                "3671C6",
                drivers.get(0)
                        .path("team_colour")
                        .asText());

        assertFalse(
                drivers.get(0)
                        .has("country_code"));

        final JsonNode manifest =
                objectMapper.readTree(
                        result.manifestFile().toFile());

        assertEquals(
                "metadata_complete",
                manifest.path("cache_state")
                        .asText());

        assertEquals(
                "2026-07-19T06:00:00Z",
                manifest.path("built_at")
                        .asText());

        assertEquals(
                3,
                manifest.path("replay")
                        .path("frame_count")
                        .asLong());

        assertEquals(
                "pending",
                manifest.path("timeline")
                        .path("state")
                        .asText());

        assertEquals(
                "F1RPLYV1",
                manifest.path("timeline")
                        .path("magic")
                        .asText());

        assertEquals(
                36,
                manifest.path("timeline")
                        .path("state_size_bytes")
                        .asInt());

        assertEquals(
                276,
                manifest.path("timeline")
                        .path("expected_bytes")
                        .asLong());

        assertFalse(
                Files.walk(
                        result.processedDirectory())
                        .anyMatch(path ->
                                path.getFileName()
                                        .toString()
                                        .contains(".part-")));
    }

    /**
     * Incomplete raw caches must be rejected.
     *
     * @throws Exception when fixture files cannot be created
     */
    @Test
    void rejectsIncompleteRawCache()
            throws Exception {

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final Path cacheDirectory =
                createRawCache(
                        objectMapper,
                        "raw_partial");

        final ProcessedReplayCacheBuilder builder =
                new ProcessedReplayCacheBuilder(
                        objectMapper,
                        Clock.systemUTC());

        assertThrows(
                IOException.class,
                () -> builder.build(
                        cacheDirectory));
    }

    private Path createRawCache(
            final ObjectMapper objectMapper,
            final String cacheState)
            throws Exception {

        final Path cacheDirectory =
                temporaryDirectory.resolve(
                        cacheState);

        final Path rawDirectory =
                cacheDirectory.resolve("raw");

        Files.createDirectories(
                rawDirectory);

        final ObjectNode manifest =
                objectMapper.createObjectNode();

        manifest.put(
                "source",
                "OpenF1");

        manifest.put(
                "cache_state",
                cacheState);

        manifest.put(
                "raw_dataset_count",
                10);

        final ObjectNode query =
                manifest.putObject("query");

        query.put("year", 2024);
        query.put(
                "country_name",
                "Testland");

        query.put(
                "session_name",
                "Race");

        final ObjectNode session =
                manifest.putObject("session");

        session.put(
                "session_key",
                9574);

        session.put(
                "circuit_short_name",
                "Example Circuit");

        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(
                        cacheDirectory
                                .resolve("manifest.json")
                                .toFile(),
                        manifest);

        Files.writeString(
                rawDirectory.resolve(
                        "drivers.json"),
                """
                [
                  {
                    "driver_number":44,
                    "name_acronym":"V01",
                    "first_name":"Lewis",
                    "last_name":"Hamilton",
                    "full_name":"Lewis HAMILTON",
                    "broadcast_name":"L HAMILTON",
                    "country_code":"GBR",
                    "team_name":"Vector Motorsport",
                    "team_colour":"27F4D2"
                  },
                  {
                    "driver_number":1,
                    "name_acronym":"A01",
                    "first_name":"Max",
                    "last_name":"Example",
                    "full_name":"Alex EXAMPLE",
                    "broadcast_name":"A EXAMPLE",
                    "country_code":null,
                    "team_name":"Apex Dynamics",
                    "team_colour":"3671c6"
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        Files.writeString(
                rawDirectory.resolve(
                        "location.json"),
                """
                [
                  {
                    "date":"2024-07-28T13:00:00.500Z",
                    "driver_number":1,
                    "x":1,
                    "y":2,
                    "z":3
                  },
                  {
                    "date":"2024-07-28T13:00:01.250Z",
                    "driver_number":44,
                    "x":4,
                    "y":5,
                    "z":6
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
                    "date":"2024-07-28T13:00:00.250Z",
                    "driver_number":1,
                    "speed":100
                  },
                  {
                    "date":"2024-07-28T13:00:01Z",
                    "driver_number":44,
                    "speed":200
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        return cacheDirectory;
    }
}
