package io.github.iamseverind.racereplay.processed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests normalized offline race-control event generation.
 */
final class ReplayRaceControlEventBuilderTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Builds sorted events and derives the checkered lap count.
     *
     * @throws Exception when fixture processing fails
     */
    @Test
    void buildsEventsAndScheduledLaps()
            throws Exception {

        final Path raw =
                temporaryDirectory.resolve(
                        "raw");

        final Path processed =
                temporaryDirectory.resolve(
                        "processed");

        Files.createDirectories(raw);
        Files.createDirectories(processed);

        Files.writeString(
                raw.resolve(
                        "race-control.json"),
                """
                [
                  {
                    "date":"2026-07-19T14:28:36Z",
                    "category":"Flag",
                    "flag":"CHEQUERED",
                    "scope":"Track",
                    "sector":null,
                    "lap_number":44,
                    "message":"CHEQUERED FLAG"
                  },
                  {
                    "date":"2026-07-19T12:59:59Z",
                    "category":"SessionStatus",
                    "flag":null,
                    "scope":null,
                    "sector":null,
                    "lap_number":1,
                    "message":"SESSION STARTED"
                  }
                ]
                """,
                StandardCharsets.UTF_8);

        Files.writeString(
                processed.resolve(
                        "replay-manifest.json"),
                """
                {
                  "replay": {
                    "start":"2026-07-19T13:00:00Z"
                  },
                  "events": {
                    "state":"pending",
                    "path":"events.json"
                  }
                }
                """,
                StandardCharsets.UTF_8);

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final int eventCount =
                new ReplayRaceControlEventBuilder(
                        objectMapper,
                        Clock.fixed(
                                Instant.parse(
                                        "2026-08-07T20:00:00Z"),
                                ZoneOffset.UTC))
                        .build(
                                temporaryDirectory);

        assertEquals(2, eventCount);

        final JsonNode events =
                objectMapper.readTree(
                        processed.resolve(
                                "events.json")
                                .toFile());

        assertEquals(
                -1.0,
                events.path("events")
                        .get(0)
                        .path("replay_seconds")
                        .asDouble());

        final JsonNode manifest =
                objectMapper.readTree(
                        processed.resolve(
                                "replay-manifest.json")
                                .toFile());

        assertEquals(
                "complete",
                manifest.path("events")
                        .path("state")
                        .asText());

        assertEquals(
                44,
                manifest.path("replay")
                        .path("scheduled_laps")
                        .asInt());
    }
}
